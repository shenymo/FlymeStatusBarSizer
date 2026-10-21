package com.example.flymestatusbarsizer.feature.onehanded;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.icu.text.Transliterator;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;
import com.example.flymestatusbarsizer.config.SettingsStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A-Z application list shown over the borrowed HOME task in a workspace pane. */
final class OneStepAppSequenceView extends FrameLayout {
    interface Listener { void onAppSelected(AppEntry app); }

    static final class AppEntry {
        final ComponentName component;
        final UserHandle user;
        final String label;
        final String latinLabel;
        final LauncherActivityInfo launcherInfo;
        volatile Drawable icon;
        volatile boolean iconLoading;
        final char section;

        AppEntry(ComponentName component, UserHandle user, String label, String latinLabel,
                 LauncherActivityInfo launcherInfo, char section) {
            this.component = component;
            this.user = user;
            this.label = label;
            this.latinLabel = latinLabel;
            this.launcherInfo = launcherInfo;
            this.section = section;
        }
    }

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BACKGROUND = 0xc8202830;
    private static final int SURFACE = 0xff1b1e21;
    private static final int PRIMARY = 0xfff2f3f4;
    private static final int SECONDARY = 0xff9ba1a6;
    private static final int ACCENT = 0xff36b7a2;
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
    private final LinearLayout list;
    private final ScrollView scroll;
    private TextView preview;
    private final EditText search;
    private float cornerRadius;
    private final java.util.Map<Character, View> sectionViews = new java.util.HashMap<>();
    private final Map<AppEntry, ImageView> iconViews = new HashMap<>();
    private final ArrayList<TextView> railItems = new ArrayList<>();
    private final ArrayList<AppEntry> apps = new ArrayList<>();
    private String query = "";
    private char activeLetter = 'A';
    private boolean loading = true;
    private int style = SettingsStore.WORKSPACE_APP_SEQUENCE_STYLE_LIST;
    private int textSizeSp = SettingsStore.DEFAULT_WORKSPACE_APP_SEQUENCE_TEXT_SIZE_SP;
    private int iconSizeDp = SettingsStore.DEFAULT_WORKSPACE_APP_SEQUENCE_ICON_SIZE_DP;

    private static final class CachedApps {
        final ArrayList<AppEntry> apps;
        final long loadedAt;

        CachedApps(ArrayList<AppEntry> apps) {
            this.apps = apps;
            loadedAt = android.os.SystemClock.uptimeMillis();
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
        column.setPadding(dp(16), dp(12), dp(8), dp(8));
        addView(column, new FrameLayout.LayoutParams(-1, -1));

        search = new EditText(context);
        search.setSingleLine(true);
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        search.setTextColor(PRIMARY);
        search.setHintTextColor(0xff747a80);
        search.setHint("搜索应用名称、拼音");
        search.setCompoundDrawablePadding(dp(8));
        search.setPadding(dp(12), 0, dp(12), 0);
        search.setBackground(roundRect(SURFACE, dp(8)));
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        column.addView(search, new LinearLayout.LayoutParams(-1, dp(38)));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                query = s.toString().trim().toLowerCase(Locale.ROOT);
                rebuild();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        FrameLayout listFrame = new FrameLayout(context);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(-1, 0, 1);
        listParams.topMargin = dp(10);
        column.addView(listFrame, listParams);

        scroll = new ScrollView(context);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));
        scroll.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (style == SettingsStore.WORKSPACE_APP_SEQUENCE_STYLE_GRID
                    && right - left != oldRight - oldLeft && !loading) post(this::rebuild);
        });
        FrameLayout.LayoutParams scrollParams = new FrameLayout.LayoutParams(-1, -1);
        scrollParams.rightMargin = dp(20);
        listFrame.addView(scroll, scrollParams);

        LinearLayout rail = new LinearLayout(context);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams railParams = new FrameLayout.LayoutParams(dp(18), -1, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        listFrame.addView(rail, railParams);
        for (int i = 0; i < ALPHABET.length(); i++) {
            char letter = ALPHABET.charAt(i);
            TextView item = text(String.valueOf(letter), 8, SECONDARY, false);
            item.setGravity(Gravity.CENTER);
            item.setTag(letter);
            railItems.add(item);
            rail.addView(item, new LinearLayout.LayoutParams(-1, 0, 1));
        }
        rail.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                int index = Math.max(0, Math.min(ALPHABET.length() - 1,
                        (int) (event.getY() / Math.max(1f, v.getHeight()) * ALPHABET.length())));
                jumpTo(ALPHABET.charAt(index));
                preview.setText(String.valueOf(ALPHABET.charAt(index)));
                preview.setVisibility(VISIBLE);
                return true;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                preview.setVisibility(GONE);
                return true;
            }
            return true;
        });

        preview = text("A", 20, Color.WHITE, true);
        preview.setGravity(Gravity.CENTER);
        preview.setBackground(roundRect(ACCENT, dp(24)));
        preview.setVisibility(GONE);
        FrameLayout.LayoutParams previewParams = new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.CENTER);
        listFrame.addView(preview, previewParams);
        scroll.setOnScrollChangeListener((View v, int x, int y, int oldX, int oldY) -> {
            updateActiveLetter(y);
            loadVisibleIcons();
        });
        scroll.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (style == SettingsStore.WORKSPACE_APP_SEQUENCE_STYLE_GRID
                    && right - left != oldRight - oldLeft && !loading) post(this::rebuild);
            post(this::loadVisibleIcons);
        });
        loadApps();
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
        if (changed) rebuild();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (style == SettingsStore.WORKSPACE_APP_SEQUENCE_STYLE_GRID && width != oldWidth && !loading) {
            rebuild();
        }
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
            rebuild();
            if (android.os.SystemClock.uptimeMillis() - cached.loadedAt < APP_CACHE_REFRESH_MS) return;
        }
        loader.execute(() -> {
            ArrayList<AppEntry> found = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            LauncherApps launcherApps = getContext().getSystemService(LauncherApps.class);
            if (launcherApps != null) {
                ArrayList<UserHandle> users = new ArrayList<>();
                users.add(UserHandle.getUserHandleForUid(ownerUserId * 100000));
                if (ownerUserId == 0) users.add(UserHandle.getUserHandleForUid(999 * 100000));
                for (UserHandle user : users) {
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
                            found.add(new AppEntry(component, user, label, latin, info, section));
                        }
                    } catch (RuntimeException ignored) { }
                }
            }
            Collections.sort(found, Comparator.comparing((AppEntry app) -> app.section)
                    .thenComparing(app -> app.latinLabel, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(app -> app.label));
            synchronized (APP_CACHE_LOCK) {
                APP_CACHE.put(ownerUserId, new CachedApps(found));
            }
            main.post(() -> {
                if (!isAttachedToWindow()) return;
                apps.clear();
                apps.addAll(found);
                loading = false;
                rebuild();
                loadVisibleIcons();
            });
        });
    }

    private void rebuild() {
        list.removeAllViews();
        sectionViews.clear();
        iconViews.clear();
        ArrayList<AppEntry> filtered = new ArrayList<>();
        for (AppEntry app : apps) if (query.isEmpty() || app.label.toLowerCase(Locale.ROOT).contains(query)
                || app.latinLabel.toLowerCase(Locale.ROOT).contains(query)) filtered.add(app);
        if (filtered.isEmpty()) {
            TextView empty = text(loading ? "正在读取应用…" : "没有找到应用", 13, SECONDARY, false);
            empty.setGravity(Gravity.CENTER);
            list.addView(empty, new LinearLayout.LayoutParams(-1, dp(88)));
            return;
        }
        char current = 0;
        GridLayout grid = null;
        int columns = gridColumnCount();
        int cellWidth = Math.max(1, scroll.getWidth() / columns);
        for (AppEntry app : filtered) {
            if (app.section != current) {
                if (grid != null) {
                    list.addView(grid, new LinearLayout.LayoutParams(-1, -2));
                    grid = null;
                }
                current = app.section;
                TextView header = text(String.valueOf(current), 15, PRIMARY, true);
                header.setGravity(Gravity.BOTTOM | Gravity.LEFT);
                header.setPadding(dp(2), dp(7), 0, dp(4));
                sectionViews.put(current, header);
                list.addView(header, new LinearLayout.LayoutParams(-1, dp(27)));
            }
            if (style == SettingsStore.WORKSPACE_APP_SEQUENCE_STYLE_GRID) {
                if (grid == null) {
                    grid = new GridLayout(getContext());
                    grid.setColumnCount(columns);
                    grid.setUseDefaultMargins(false);
                }
                grid.addView(gridItem(app, cellWidth));
            } else list.addView(row(app));
        }
        if (grid != null) list.addView(grid, new LinearLayout.LayoutParams(-1, -2));
        scroll.post(this::loadVisibleIcons);
    }

    private View row(AppEntry app) {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(4), dp(4), dp(4));
        row.setMinimumHeight(dp(42));
        ImageView icon = new ImageView(getContext());
        icon.setImageResource(android.R.drawable.sym_def_app_icon);
        icon.setTag(app);
        iconViews.put(app, icon);
        row.addView(icon, new LinearLayout.LayoutParams(dp(iconSizeDp), dp(iconSizeDp)));
        TextView label = text(app.label, textSizeSp, PRIMARY, false);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1);
        labelParams.leftMargin = dp(10);
        row.addView(label, labelParams);
        row.setBackground(selectableBackground());
        row.setOnClickListener(v -> { if (listener != null) listener.onAppSelected(app); });
        return row;
    }

    private View gridItem(AppEntry app, int cellWidth) {
        LinearLayout tile = new LinearLayout(getContext());
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(4), dp(4), dp(4), dp(4));
        ImageView icon = new ImageView(getContext());
        icon.setImageResource(android.R.drawable.sym_def_app_icon);
        icon.setTag(app);
        iconViews.put(app, icon);
        tile.addView(icon, new LinearLayout.LayoutParams(dp(iconSizeDp), dp(iconSizeDp)));
        TextView label = text(app.label, textSizeSp, PRIMARY, false);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
        labelParams.topMargin = dp(4);
        tile.addView(label, labelParams);
        tile.setBackground(selectableBackground());
        tile.setOnClickListener(v -> { if (listener != null) listener.onAppSelected(app); });
        GridLayout.LayoutParams params = (GridLayout.LayoutParams) tile.getLayoutParams();
        if (params == null) params = new GridLayout.LayoutParams();
        params.width = cellWidth;
        params.height = iconSizeDp + 32 > 76 ? dp(iconSizeDp + 32) : dp(76);
        tile.setLayoutParams(params);
        return tile;
    }

    private int gridColumnCount() {
        int availableWidth = scroll.getWidth() > 0 ? scroll.getWidth() : dp(280);
        int cellWidth = dp(88);
        return Math.max(1, Math.min(5, availableWidth / cellWidth));
    }

    private void loadVisibleIcons() {
        Rect visible = new Rect();
        for (Map.Entry<AppEntry, ImageView> item : iconViews.entrySet()) {
            AppEntry app = item.getKey();
            ImageView view = item.getValue();
            if (app.icon != null) {
                if (view.getDrawable() != app.icon) view.setImageDrawable(app.icon);
                continue;
            }
            if (!app.iconLoading && view.getLocalVisibleRect(visible)) {
                app.iconLoading = true;
                loader.execute(() -> {
                    Drawable loaded = null;
                    try { loaded = app.launcherInfo.getBadgedIcon(0); }
                    catch (RuntimeException ignored) { }
                    app.icon = loaded;
                    app.iconLoading = false;
                    Drawable icon = loaded;
                    main.post(() -> {
                        if (icon != null && view.getTag() == app) view.setImageDrawable(icon);
                    });
                });
            }
        }
    }

    private void jumpTo(char requested) {
        if (!query.isEmpty()) return;
        char target = requested;
        View section = sectionViews.get(target);
        if (section == null) {
            for (int distance = 1; distance < ALPHABET.length() && section == null; distance++) {
                int earlier = target - 'A' - distance;
                int later = target - 'A' + distance;
                if (earlier >= 0) section = sectionViews.get(ALPHABET.charAt(earlier));
                if (section == null && later < ALPHABET.length()) section = sectionViews.get(ALPHABET.charAt(later));
            }
        }
        if (section != null) {
            scroll.scrollTo(0, section.getTop());
            activeLetter = target;
            updateRailColors();
        }
    }

    private void updateActiveLetter(int scrollY) {
        char active = activeLetter;
        for (char letter : ALPHABET.toCharArray()) {
            View section = sectionViews.get(letter);
            if (section != null && section.getTop() <= scrollY + dp(8)) active = letter;
        }
        activeLetter = active;
        updateRailColors();
    }

    private void updateRailColors() {
        for (TextView item : railItems) {
            boolean selected = item.getTag() instanceof Character && (Character) item.getTag() == activeLetter;
            item.setTextColor(selected ? ACCENT : SECONDARY);
            item.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
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
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
