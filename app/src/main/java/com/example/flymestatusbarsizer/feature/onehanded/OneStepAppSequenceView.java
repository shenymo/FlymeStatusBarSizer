package com.example.flymestatusbarsizer.feature.onehanded;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.icu.text.Transliterator;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.UserHandle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.inputmethod.EditorInfo;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;
import com.example.flymestatusbarsizer.config.SettingsStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A-Z application list shown over the borrowed HOME task in a workspace pane. */
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

    /** One adapter row: a section header (apps == null) or one/more apps on a line. */
    private static final class Row {
        final char section;
        final AppEntry[] apps;

        Row(char section, AppEntry[] apps) {
            this.section = section;
            this.apps = apps;
        }
    }

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ#";
    private static final int BACKGROUND = 0xc8202830;
    private static final int SURFACE = 0xff1b1e21;
    private static final int CHIP = 0xff2b3036;
    private static final int PRIMARY = 0xfff2f3f4;
    private static final int SECONDARY = 0xff9ba1a6;
    private static final int DISABLED = 0x4d9ba1a6;
    private static final int ACCENT = 0xff36b7a2;
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_LIST = 1;
    private static final int TYPE_GRID = 2;
    private static final Transliterator PINYIN = Transliterator.getInstance("Han-Latin; Latin-ASCII; Upper()");
    private static final long APP_CACHE_REFRESH_MS = 30_000L;
    private static final Object APP_CACHE_LOCK = new Object();
    private static final Map<Integer, CachedApps> APP_CACHE = new HashMap<>();
    private final int ownerUserId;
    private final Listener listener;
    private final ExecutorService loader = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "FlymeAppSequence");
        thread.setDaemon(true);
        return thread;
    });
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ListView list;
    private final TextView empty;
    private final TextView sticky;
    private final TextView preview;
    private final EditText search;
    private final ImageView clear;
    private final LinearLayout keyboard;
    private float cornerRadius;
    private final ArrayList<TextView> railItems = new ArrayList<>();
    private final ArrayList<AppEntry> apps = new ArrayList<>();
    private final ArrayList<AppEntry> filtered = new ArrayList<>();
    private final ArrayList<Row> rows = new ArrayList<>();
    private final Map<Character, Integer> sectionRows = new HashMap<>();
    private Adapter adapter;
    private String query = "";
    private char activeLetter = 0;
    private char railLetter = 0;
    private boolean loading = true;
    private int columns = 1;
    private int style = SettingsStore.WORKSPACE_APP_SEQUENCE_STYLE_LIST;
    private int textSizeSp = SettingsStore.DEFAULT_WORKSPACE_APP_SEQUENCE_TEXT_SIZE_SP;
    private int iconSizeDp = SettingsStore.DEFAULT_WORKSPACE_APP_SEQUENCE_ICON_SIZE_DP;
    private boolean keyboardVisible;
    private boolean settingKeyboardText;
    private final ArrayList<String> queryGroups = new ArrayList<>();

    private static final class CachedApps {
        final ArrayList<AppEntry> apps;
        final long loadedAt;

        CachedApps(ArrayList<AppEntry> apps) {
            this.apps = apps;
            loadedAt = SystemClock.uptimeMillis();
        }
    }

    OneStepAppSequenceView(Context context, int ownerUserId, Listener listener) {
        super(context);
        this.ownerUserId = ownerUserId;
        this.listener = listener;
        setBackgroundColor(BACKGROUND);
        setFocusable(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadius);
            }
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
        LinearLayout.LayoutParams searchBarParams = new LinearLayout.LayoutParams(-1, dp(38));
        searchBarParams.rightMargin = dp(10);
        column.addView(searchBar, searchBarParams);

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
        search.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) showCustomKeyboard();
            return false;
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!settingKeyboardText) queryGroups.clear();
                query = s.toString().trim().toLowerCase(Locale.ROOT);
                clear.setVisibility(s.length() > 0 ? VISIBLE : GONE);
                applyFilter();
                list.setSelection(0);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        search.setOnEditorActionListener((v, actionId, event) -> {
            boolean submit = actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN);
            if (!submit) return false;
            if (!query.isEmpty() && !filtered.isEmpty() && listener != null) {
                listener.onAppSelected(filtered.get(0));
            }
            return true;
        });

        clear = new ImageView(context);
        clear.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        clear.setImageTintList(ColorStateList.valueOf(SECONDARY));
        clear.setPadding(dp(8), dp(8), dp(8), dp(8));
        clear.setBackground(selectableBackground());
        clear.setContentDescription("清空搜索");
        clear.setVisibility(GONE);
        clear.setOnClickListener(v -> {
            queryGroups.clear();
            search.setText("");
        });
        searchBar.addView(clear, new LinearLayout.LayoutParams(dp(34), dp(34)));

        FrameLayout listFrame = new FrameLayout(context);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(-1, 0, 1);
        listParams.topMargin = dp(10);
        column.addView(listFrame, listParams);

        list = new ListView(context);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setSelector(new ColorDrawable(Color.TRANSPARENT));
        list.setVerticalScrollBarEnabled(false);
        list.setOverScrollMode(OVER_SCROLL_NEVER);
        list.setCacheColorHint(Color.TRANSPARENT);
        adapter = new Adapter();
        list.setAdapter(adapter);
        FrameLayout.LayoutParams scrollParams = new FrameLayout.LayoutParams(-1, -1);
        scrollParams.rightMargin = dp(22);
        listFrame.addView(list, scrollParams);
        list.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft) post(this::updateColumns);
        });

        sticky = headerText();
        FrameLayout.LayoutParams stickyParams = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT);
        listFrame.addView(sticky, stickyParams);
        styleHeader(sticky);
        sticky.setVisibility(GONE);

        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override public void onScrollStateChanged(AbsListView view, int scrollState) {}
            @Override public void onScroll(AbsListView view, int first, int visible, int total) {
                updateSticky();
            }
        });

        empty = text("正在读取应用…", 13, SECONDARY, false);
        empty.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams emptyParams = new FrameLayout.LayoutParams(-1, dp(88), Gravity.TOP);
        emptyParams.rightMargin = dp(22);
        listFrame.addView(empty, emptyParams);

        LinearLayout rail = new LinearLayout(context);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setGravity(Gravity.CENTER);
        listFrame.addView(rail, new FrameLayout.LayoutParams(dp(22), -1, Gravity.RIGHT | Gravity.CENTER_VERTICAL));
        for (int i = 0; i < ALPHABET.length(); i++) {
            char letter = ALPHABET.charAt(i);
            TextView item = text(String.valueOf(letter), 10, SECONDARY, false);
            item.setGravity(Gravity.CENTER);
            item.setTag(letter);
            railItems.add(item);
            rail.addView(item, new LinearLayout.LayoutParams(-1, 0, 1));
        }

        preview = text("A", 22, Color.WHITE, true);
        preview.setGravity(Gravity.CENTER);
        preview.setBackground(roundRect(ACCENT, dp(26)));
        preview.setVisibility(GONE);
        FrameLayout.LayoutParams previewParams = new FrameLayout.LayoutParams(dp(52), dp(52), Gravity.TOP | Gravity.RIGHT);
        previewParams.rightMargin = dp(34);
        listFrame.addView(preview, previewParams);

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

        rail.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                if (!query.isEmpty()) return true;
                int index = Math.max(0, Math.min(ALPHABET.length() - 1,
                        (int) (event.getY() / Math.max(1f, v.getHeight()) * ALPHABET.length())));
                char letter = ALPHABET.charAt(index);
                if (letter != railLetter) {
                    railLetter = letter;
                    v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    jumpTo(letter);
                }
                preview.setText(String.valueOf(letter));
                float y = event.getY() + v.getTop() - dp(26);
                preview.setTranslationY(Math.max(0, Math.min(listFrame.getHeight() - dp(52), y)));
                preview.setVisibility(VISIBLE);
                return true;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                railLetter = 0;
                preview.setVisibility(GONE);
            }
            return true;
        });
        loadApps();
    }

    boolean consumeBack() {
        if (!keyboardVisible) return false;
        hideCustomKeyboard();
        return true;
    }

    void dismissKeyboard() {
        if (keyboardVisible) hideCustomKeyboard();
    }

    private void showCustomKeyboard() {
        keyboardVisible = true;
        keyboard.setVisibility(VISIBLE);
        search.requestFocus();
    }

    private void hideCustomKeyboard() {
        keyboardVisible = false;
        keyboard.setVisibility(GONE);
    }

    private void addKeyboardRow(String... keys) {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        keyboard.addView(row, new LinearLayout.LayoutParams(-1, 0, 1f));
        for (String key : keys) {
            TextView button = text(key, 17, PRIMARY, false);
            button.setGravity(Gravity.CENTER);
            button.setBackground(roundRect(CHIP, dp(8)));
            button.setContentDescription("输入 " + key);
            button.setTag(key);
            button.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                onKeyboardKey((String) v.getTag());
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1f);
            params.setMargins(dp(2), dp(2), dp(2), dp(2));
            row.addView(button, params);
        }
    }

    private void onKeyboardKey(String key) {
        if ("DEL".equals(key)) {
            if (!queryGroups.isEmpty()) queryGroups.remove(queryGroups.size() - 1);
            updateKeyboardText();
            return;
        }
        queryGroups.add(key.toLowerCase(Locale.ROOT));
        updateKeyboardText();
    }

    private void updateKeyboardText() {
        StringBuilder value = new StringBuilder();
        for (String group : queryGroups) value.append(group);
        settingKeyboardText = true;
        try { search.setText(value); }
        finally { settingKeyboardText = false; }
        search.setSelection(search.length());
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(this::applyGlassBackground);
    }

    void dispose() {
        loader.shutdownNow();
    }

    void setCornerRadius(float radius) {
        cornerRadius = radius;
        invalidateOutline();
        Drawable background = getBackground();
        if (background != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            FlymeStatusBarSizer.invokeMethodCompat(background, "setCornerRadius",
                    new Class<?>[]{float.class}, cornerRadius);
        }
    }

    private void applyGlassBackground() {
        if (!isAttachedToWindow() || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        Object viewRoot = FlymeStatusBarSizer.invokeNoArgCompat(this, "getViewRootImpl");
        Object blur = FlymeStatusBarSizer.invokeNoArgCompat(viewRoot, "createBackgroundBlurDrawable");
        if (!(blur instanceof Drawable)) return;
        FlymeStatusBarSizer.invokeMethodCompat(blur, "setCornerRadius",
                new Class<?>[]{float.class}, cornerRadius);
        FlymeStatusBarSizer.invokeMethodCompat(blur, "setBlurRadius",
                new Class<?>[]{int.class}, dp(32));
        FlymeStatusBarSizer.invokeMethodCompat(blur, "setZAdjustment",
                new Class<?>[]{int.class}, -1);
        FlymeStatusBarSizer.invokeMethodCompat(blur, "setColor",
                new Class<?>[]{int.class}, BACKGROUND);
        setBackground((Drawable) blur);
    }

    void configure(int style, int textSizeSp, int iconSizeDp) {
        boolean changed = this.style != style || this.textSizeSp != textSizeSp
                || this.iconSizeDp != iconSizeDp;
        this.style = style;
        this.textSizeSp = textSizeSp;
        this.iconSizeDp = iconSizeDp;
        if (!changed) return;
        styleHeader(sticky);
        columns = computeColumns();
        rebuildRows();
    }

    private void loadApps() {
        CachedApps cached;
        synchronized (APP_CACHE_LOCK) {
            cached = APP_CACHE.get(ownerUserId);
        }
        if (cached != null) {
            apps.clear();
            apps.addAll(cached.apps);
            loading = false;
            applyFilter();
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
                            if (packageName.equals("com.meizu.flyme.launcher")
                                    || packageName.equals("com.android.systemui")
                                    || packageName.equals(getContext().getPackageName())) continue;
                            String key = user.toString() + ":" + component.flattenToString();
                            if (!seen.add(key)) continue;
                            String label = String.valueOf(info.getLabel());
                            String latin = transliterate(label);
                            char section = latin.isEmpty() ? '#' : latin.charAt(0);
                            if (section < 'A' || section > 'Z') section = '#';
                            String display = u > 0 ? label + "（分身）" : label;
                            found.add(new AppEntry(component, user, label, display, latin,
                                    initials(label), info, section));
                        }
                    } catch (RuntimeException ignored) { }
                }
            }
            Collections.sort(found, Comparator.comparing((AppEntry app) -> sectionOrder(app.section))
                    .thenComparing(app -> app.latinLabel, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(app -> app.displayLabel));
            synchronized (APP_CACHE_LOCK) {
                APP_CACHE.put(ownerUserId, new CachedApps(found));
            }
            main.post(() -> {
                if (!isAttachedToWindow()) return;
                apps.clear();
                apps.addAll(found);
                loading = false;
                applyFilter();
            });
        });
    }

    private static int sectionOrder(char section) {
        return section == '#' ? 'Z' + 1 : section;
    }

    /** Filters by name, full pinyin and pinyin initials; prefix matches rank first. */
    private void applyFilter() {
        filtered.clear();
        if (query.isEmpty()) {
            filtered.addAll(apps);
        } else {
            ArrayList<AppEntry> prefix = new ArrayList<>();
            ArrayList<AppEntry> contains = new ArrayList<>();
            for (AppEntry app : apps) {
                if (matchesQuery(app.lowerLabel, true) || matchesQuery(app.lowerLatin, true)
                        || matchesQuery(app.lowerInitials, true)) prefix.add(app);
                else if (matchesQuery(app.lowerLabel, false) || matchesQuery(app.lowerLatin, false)
                        || matchesQuery(app.lowerInitials, false)) contains.add(app);
            }
            filtered.addAll(prefix);
            filtered.addAll(contains);
        }
        rebuildRows();
    }

    private boolean matchesQuery(String value, boolean prefix) {
        if (queryGroups.isEmpty()) {
            return prefix ? value.startsWith(query) : value.contains(query);
        }
        int limit = value.length() - queryGroups.size();
        if (limit < 0) return false;
        for (int offset = 0; offset <= limit; offset++) {
            if (prefix && offset > 0) break;
            boolean matched = true;
            for (int i = 0; i < queryGroups.size(); i++) {
                if (queryGroups.get(i).indexOf(value.charAt(offset + i)) < 0) {
                    matched = false;
                    break;
                }
            }
            if (matched) return true;
        }
        return false;
    }

    private void rebuildRows() {
        rows.clear();
        sectionRows.clear();
        boolean grid = style == SettingsStore.WORKSPACE_APP_SEQUENCE_STYLE_GRID;
        int perRow = grid ? columns : 1;
        boolean sections = query.isEmpty();
        char current = 0;
        ArrayList<AppEntry> pending = new ArrayList<>();
        for (AppEntry app : filtered) {
            if (sections && app.section != current) {
                flushRow(pending, current);
                current = app.section;
                sectionRows.put(current, rows.size());
                rows.add(new Row(current, null));
            }
            pending.add(app);
            if (pending.size() >= perRow) flushRow(pending, current);
        }
        flushRow(pending, current);
        empty.setText(loading ? "正在读取应用…" : "没有找到应用");
        empty.setVisibility(filtered.isEmpty() ? VISIBLE : GONE);
        adapter.notifyDataSetChanged();
        updateRailColors();
        list.post(this::updateSticky);
    }

    private void flushRow(ArrayList<AppEntry> pending, char section) {
        if (pending.isEmpty()) return;
        rows.add(new Row(section, pending.toArray(new AppEntry[0])));
        pending.clear();
    }

    private void updateColumns() {
        int next = computeColumns();
        if (next == columns) return;
        columns = next;
        if (style == SettingsStore.WORKSPACE_APP_SEQUENCE_STYLE_GRID) rebuildRows();
    }

    private int computeColumns() {
        int availableWidth = list.getWidth() > 0 ? list.getWidth() : dp(280);
        return Math.max(1, Math.min(5, availableWidth / dp(88)));
    }

    /** Pins the current section chip to the top and pushes it up when the next header arrives. */
    private void updateSticky() {
        if (sticky == null) return;
        int first = list.getFirstVisiblePosition();
        if (!query.isEmpty() || rows.isEmpty() || first >= rows.size()) {
            sticky.setVisibility(GONE);
            return;
        }
        char section = rows.get(first).section;
        sticky.setText(String.valueOf(section));
        sticky.setVisibility(VISIBLE);
        float offset = 0;
        View next = list.getChildAt(1);
        if (first + 1 < rows.size() && rows.get(first + 1).apps == null && next != null
                && next.getTop() < sticky.getBottom()) {
            offset = next.getTop() - sticky.getBottom();
        }
        sticky.setTranslationY(offset);
        if (section != activeLetter) {
            activeLetter = section;
            updateRailColors();
        }
    }

    private void jumpTo(char requested) {
        if (!query.isEmpty() || sectionRows.isEmpty()) return;
        int index = ALPHABET.indexOf(requested);
        Integer row = null;
        for (int i = index; i < ALPHABET.length() && row == null; i++) row = sectionRows.get(ALPHABET.charAt(i));
        for (int i = index - 1; i >= 0 && row == null; i--) row = sectionRows.get(ALPHABET.charAt(i));
        if (row != null) list.setSelection(row);
    }

    private void updateRailColors() {
        for (TextView item : railItems) {
            char letter = (Character) item.getTag();
            boolean available = sectionRows.containsKey(letter);
            boolean selected = available && letter == activeLetter;
            item.setTextColor(selected ? ACCENT : available ? SECONDARY : DISABLED);
            item.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    private void bindIcon(ImageView view, AppEntry app) {
        view.setTag(app);
        if (app.icon != null) {
            view.setImageDrawable(app.icon);
            return;
        }
        view.setImageResource(android.R.drawable.sym_def_app_icon);
        if (app.iconLoading) return;
        app.iconLoading = true;
        loader.execute(() -> {
            Drawable loaded = null;
            try { loaded = app.launcherInfo.getBadgedIcon(0); }
            catch (RuntimeException ignored) { }
            app.icon = loaded;
            app.iconLoading = false;
            Drawable icon = loaded;
            main.post(() -> {
                if (icon == null) return;
                for (int i = 0; i < list.getChildCount(); i++) refreshIcon(list.getChildAt(i), app, icon);
            });
        });
    }

    private static void refreshIcon(View view, AppEntry app, Drawable icon) {
        if (view instanceof ImageView) {
            if (view.getTag() == app) ((ImageView) view).setImageDrawable(icon);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) refreshIcon(group.getChildAt(i), app, icon);
        }
    }

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int position) { return rows.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public int getViewTypeCount() { return 3; }
        @Override public boolean areAllItemsEnabled() { return false; }
        @Override public boolean isEnabled(int position) { return false; }

        @Override public int getItemViewType(int position) {
            if (rows.get(position).apps == null) return TYPE_HEADER;
            return style == SettingsStore.WORKSPACE_APP_SEQUENCE_STYLE_GRID ? TYPE_GRID : TYPE_LIST;
        }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            Row row = rows.get(position);
            int type = getItemViewType(position);
            if (type == TYPE_HEADER) {
                FrameLayout header = convertView instanceof FrameLayout ? (FrameLayout) convertView : headerRow();
                TextView chip = (TextView) header.getChildAt(0);
                styleHeader(chip);
                chip.setText(String.valueOf(row.section));
                return header;
            }
            if (type == TYPE_LIST) {
                LinearLayout view = convertView instanceof LinearLayout
                        && ((LinearLayout) convertView).getChildCount() == 2
                        && convertView.getTag() == null ? (LinearLayout) convertView : listRow();
                bindListRow(view, row.apps[0]);
                return view;
            }
            LinearLayout view = convertView instanceof LinearLayout
                    && ((LinearLayout) convertView).getChildCount() == columns
                    && "grid".equals(convertView.getTag()) ? (LinearLayout) convertView : gridRow();
            for (int i = 0; i < columns; i++) {
                LinearLayout tile = (LinearLayout) view.getChildAt(i);
                if (i < row.apps.length) {
                    tile.setVisibility(VISIBLE);
                    bindGridTile(tile, row.apps[i]);
                } else {
                    tile.setVisibility(INVISIBLE);
                    tile.setOnClickListener(null);
                }
            }
            return view;
        }
    }

    private LinearLayout listRow() {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(4), dp(4), dp(4));
        row.setMinimumHeight(dp(44));
        row.setBackground(selectableBackground());
        row.addView(new ImageView(getContext()), new LinearLayout.LayoutParams(dp(iconSizeDp), dp(iconSizeDp)));
        TextView label = text("", textSizeSp, PRIMARY, false);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1);
        labelParams.leftMargin = dp(12);
        row.addView(label, labelParams);
        return row;
    }

    private void bindListRow(LinearLayout row, AppEntry app) {
        ImageView icon = (ImageView) row.getChildAt(0);
        LinearLayout.LayoutParams iconParams = (LinearLayout.LayoutParams) icon.getLayoutParams();
        if (iconParams.width != dp(iconSizeDp)) {
            iconParams.width = iconParams.height = dp(iconSizeDp);
            icon.setLayoutParams(iconParams);
        }
        bindIcon(icon, app);
        TextView label = (TextView) row.getChildAt(1);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
        label.setText(app.displayLabel);
        row.setContentDescription(app.displayLabel);
        row.setOnClickListener(v -> { if (listener != null) listener.onAppSelected(app); });
    }

    private LinearLayout gridRow() {
        LinearLayout row = new LinearLayout(getContext());
        row.setTag("grid");
        for (int i = 0; i < columns; i++) {
            LinearLayout tile = new LinearLayout(getContext());
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER);
            tile.setPadding(dp(4), dp(6), dp(4), dp(6));
            tile.setBackground(selectableBackground());
            tile.addView(new ImageView(getContext()), new LinearLayout.LayoutParams(dp(iconSizeDp), dp(iconSizeDp)));
            TextView label = text("", textSizeSp, PRIMARY, false);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
            labelParams.topMargin = dp(4);
            tile.addView(label, labelParams);
            row.addView(tile, new LinearLayout.LayoutParams(0, Math.max(dp(76), dp(iconSizeDp + 32)), 1));
        }
        return row;
    }

    private void bindGridTile(LinearLayout tile, AppEntry app) {
        int height = Math.max(dp(76), dp(iconSizeDp + 32));
        LinearLayout.LayoutParams tileParams = (LinearLayout.LayoutParams) tile.getLayoutParams();
        if (tileParams.height != height) {
            tileParams.height = height;
            tile.setLayoutParams(tileParams);
        }
        ImageView icon = (ImageView) tile.getChildAt(0);
        LinearLayout.LayoutParams iconParams = (LinearLayout.LayoutParams) icon.getLayoutParams();
        if (iconParams.width != dp(iconSizeDp)) {
            iconParams.width = iconParams.height = dp(iconSizeDp);
            icon.setLayoutParams(iconParams);
        }
        bindIcon(icon, app);
        TextView label = (TextView) tile.getChildAt(1);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
        label.setText(app.displayLabel);
        tile.setContentDescription(app.displayLabel);
        tile.setOnClickListener(v -> { if (listener != null) listener.onAppSelected(app); });
    }

    private FrameLayout headerRow() {
        FrameLayout row = new FrameLayout(getContext());
        row.addView(headerText(), new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT));
        return row;
    }

    private TextView headerText() {
        TextView chip = text("", 13, PRIMARY, true);
        chip.setGravity(Gravity.CENTER);
        chip.setBackground(roundRect(CHIP, dp(11)));
        return chip;
    }

    /** List headers are a little roomier; grid headers stay compact so tiles keep the space. */
    private void styleHeader(TextView chip) {
        boolean grid = style == SettingsStore.WORKSPACE_APP_SEQUENCE_STYLE_GRID;
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, grid ? 11 : 13);
        chip.setMinWidth(dp(grid ? 20 : 22));
        chip.setMinHeight(dp(grid ? 20 : 22));
        chip.setPadding(dp(6), 0, dp(6), 0);
        ViewGroup.LayoutParams params = chip.getLayoutParams();
        if (params instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams frame = (FrameLayout.LayoutParams) params;
            int top = dp(grid ? 4 : 8);
            int bottom = dp(grid ? 2 : 4);
            if (frame.topMargin != top || frame.bottomMargin != bottom || frame.leftMargin != dp(2)) {
                frame.topMargin = top;
                frame.bottomMargin = bottom;
                frame.leftMargin = dp(2);
                chip.setLayoutParams(frame);
            }
        }
    }

    private static String transliterate(String value) {
        try {
            String latin;
            synchronized (PINYIN) {
                latin = PINYIN.transliterate(value);
            }
            latin = latin.replaceAll("[^A-Z0-9]", "");
            return latin.isEmpty() ? value.toUpperCase(Locale.ROOT) : latin;
        } catch (RuntimeException ignored) { return value.toUpperCase(Locale.ROOT); }
    }

    /** First letter of each Chinese character / Latin word, e.g. 支付宝 -> ZFB, Google Play -> GP. */
    private static String initials(String label) {
        StringBuilder out = new StringBuilder();
        boolean wordStart = true;
        for (int i = 0; i < label.length(); ) {
            int cp = label.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN) {
                String latin = transliterate(new String(Character.toChars(cp)));
                if (!latin.isEmpty()) out.append(latin.charAt(0));
                wordStart = true;
            } else if (Character.isLetterOrDigit(cp)) {
                if (wordStart || Character.isUpperCase(cp)) out.appendCodePoint(Character.toUpperCase(cp));
                wordStart = false;
            } else {
                wordStart = true;
            }
        }
        return out.toString();
    }

    private TextView text(String value, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private Drawable selectableBackground() {
        TypedValue value = new TypedValue();
        getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
        return value.resourceId != 0 ? getContext().getDrawable(value.resourceId) : null;
    }

    private Drawable roundRect(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
