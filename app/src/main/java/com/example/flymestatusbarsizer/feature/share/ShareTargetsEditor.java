package com.example.flymestatusbarsizer.feature.share;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.window.OnBackInvokedDispatcher;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import com.example.flymestatusbarsizer.MainActivity;
import com.example.flymestatusbarsizer.R;
import com.example.flymestatusbarsizer.config.SettingsStore;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class ShareTargetsEditor {
    private final MainActivity activity;
    private final Executor scanner;
    private final BiFunction<android.content.Context, ShareContentType, ShareTargetCatalog.Snapshot> catalog;
    private final Map<ShareContentType, ShareTargetCatalog.Snapshot> catalogs = new EnumMap<>(ShareContentType.class);
    private ShareTargetCatalog.Session scanSession;
    private ShareTargetProfiles profiles;
    private ShareTargetRules defaults;
    private ShareContentType selected = ShareContentType.DEFAULT;
    private final Map<ShareContentType, TextView> typeTabs = new EnumMap<>(ShareContentType.class);
    private LinearLayout profileRow;
    private TextView profileStatus;
    private Switch independent;
    private boolean updatingSelection;
    private final Map<String, ShareTargetCatalog.Target> targets = new LinkedHashMap<>();
    private final List<String> visible = new ArrayList<>();
    private final TargetAdapter adapter = new TargetAdapter();
    private Dialog dialog;
    private Dialog appManager;
    private GridView list;
    private TextView status;
    private ImageView byApp;
    private ImageView save;
    private TextView changes;
    private TextView shownTab;
    private TextView hiddenTab;
    private TextView empty;
    private ImageView clearSearch;
    private LinearLayout undoBar;
    private TextView undoMessage;
    private Runnable undoAction;
    private final Runnable clearUndo = this::dismissUndo;
    private String initialState;
    private boolean showHidden;
    private FrameLayout overlayRoot;
    private View sheetScrim;
    private LinearLayout sheetBody;
    private AlertDialog exitPrompt;
    private EditText search;
    private ShareTargetsDraft draft;
    private volatile boolean closed;
    private boolean loading;
    private String dragComponent;
    private String dropComponent;
    private float dragX;
    private float dragY;

    public ShareTargetsEditor(MainActivity activity) {
        this.activity = activity;
        this.scanner = command -> new Thread(command, "share-target-scan").start();
        this.catalog = null;
    }

    ShareTargetsEditor(MainActivity activity, Executor scanner,
            Function<android.content.Context, List<ShareTargetCatalog.Target>> catalog) {
        this(activity, scanner, (context, type) -> catalog.apply(context));
    }

    ShareTargetsEditor(MainActivity activity, Executor scanner,
            BiFunction<android.content.Context, ShareContentType, List<ShareTargetCatalog.Target>> catalog) {
        this.activity = activity;
        this.scanner = scanner;
        this.catalog = (context, type) -> new ShareTargetCatalog.Snapshot(catalog.apply(context, type), true);
    }

    public void show() {
        defaults = ShareTargetRules.parse(
                SettingsStore.readString(activity.prefs(), SettingsStore.KEY_SHARE_TARGET_ORDER, ""),
                SettingsStore.readString(activity.prefs(), SettingsStore.KEY_SHARE_HIDDEN_TARGETS, ""));
        profiles = ShareTargetProfiles.parse(SettingsStore.readString(activity.prefs(),
                SettingsStore.KEY_SHARE_TARGET_PROFILES, ""));
        dialog = new Dialog(activity) {
            @Override public void onBackPressed() { requestClose(); }
        };
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(false);
        LinearLayout root = column();
        root.setFitsSystemWindows(true);
        root.setPadding(dp(16), dp(4), dp(16), 0);
        root.setBackgroundColor(activity.surfaceColor());

        LinearLayout titleRow = row();
        ImageView back = iconButton(R.drawable.ic_settings_back, "返回");
        activity.setTapClickListener(back, v -> requestClose());
        titleRow.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout heading = column();
        TextView title = text("分享列表管理", 19, activity.textColor());
        title.setTypeface(null, Typeface.BOLD);
        title.setSingleLine(true);
        heading.addView(title);
        changes = text("", 11, activity.subtextColor());
        changes.setSingleLine(true);
        heading.addView(changes);
        titleRow.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        byApp = iconButton(R.drawable.ic_share_apps, "按应用管理");
        byApp.setPadding(dp(10), dp(12), dp(10), dp(12));
        byApp.setEnabled(false);
        byApp.setAlpha(0.4f);
        activity.setTapClickListener(byApp, v -> openAppManager());
        titleRow.addView(byApp, new LinearLayout.LayoutParams(dp(44), dp(48)));
        ImageView more = iconButton(R.drawable.ic_settings_more, "更多操作");
        more.setPadding(dp(10), dp(12), dp(10), dp(12));
        activity.setTapClickListener(more, v -> showMoreMenu());
        titleRow.addView(more, new LinearLayout.LayoutParams(dp(44), dp(48)));
        save = iconButton(R.drawable.ic_share_save, "保存所有类型的修改");
        save.setPadding(dp(10), dp(12), dp(10), dp(12));
        save.setEnabled(false);
        save.setAlpha(0.4f);
        activity.setTapClickListener(save, v -> save());
        titleRow.addView(save, new LinearLayout.LayoutParams(dp(44), dp(48)));
        root.addView(titleRow, activity.matchWrap());

        initialState = state();
        HorizontalScrollView types = new HorizontalScrollView(activity);
        types.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs = row();
        for (ShareContentType type : ShareContentType.values()) {
            TextView tab = button(type == ShareContentType.DEFAULT ? "默认" : type.label, false);
            tab.setMinHeight(dp(36));
            tab.setMinWidth(dp(52));
            tab.setPadding(dp(10), dp(4), dp(10), dp(4));
            tab.setTextSize(13);
            tab.setContentDescription("分享类型：" + type.label);
            activity.setTapClickListener(tab, v -> {
                if (loading || catalogs.isEmpty() || selected == type) return;
                dismissUndo();
                stash();
                selected = type;
                // The previous draft was stashed under its own type above.
                draft = null;
                targets.clear();
                visible.clear();
                if (catalogs.containsKey(type)) selectDraft();
                else scanSelected();
            });
            typeTabs.put(type, tab);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMarginEnd(dp(6));
            tabs.addView(tab, lp);
        }
        types.addView(tabs);
        root.addView(types, activity.matchWrapWithTop(8));

        profileRow = row();
        profileRow.setPadding(dp(12), dp(4), dp(8), dp(4));
        profileRow.setBackground(activity.roundRect(activity.surfaceSoftColor(), 12));
        profileStatus = text("沿用默认", 12, activity.subtextColor());
        profileRow.addView(profileStatus, new LinearLayout.LayoutParams(0, -2, 1));
        independent = new Switch(activity);
        independent.setText("单独设置此类型");
        independent.setTextColor(activity.textColor());
        independent.setTextSize(12);
        independent.setMinHeight(dp(36));
        independent.setOnCheckedChangeListener((view, checked) -> {
            if (updatingSelection || loading || draft == null) return;
            dismissUndo();
            profiles.set(selected, checked ? draft.rules() : null);
            selectDraft();
        });
        activity.styleSwitch(independent);
        profileRow.addView(independent);
        profileRow.setVisibility(View.GONE);
        root.addView(profileRow, activity.matchWrapWithTop(6));

        LinearLayout searchRow = row();
        searchRow.setPadding(dp(10), 0, dp(4), 0);
        searchRow.setBackground(activity.roundRect(activity.surfaceSoftColor(), 12));
        ImageView searchIcon = new ImageView(activity);
        searchIcon.setImageResource(R.drawable.ic_feature_search);
        searchIcon.setColorFilter(activity.subtextColor());
        searchIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        searchRow.addView(searchIcon, new LinearLayout.LayoutParams(dp(18), dp(18)));
        search = new EditText(activity);
        search.setSingleLine(true);
        search.setTextSize(13);
        search.setTextColor(activity.textColor());
        search.setHintTextColor(activity.subtextColor());
        search.setHint("搜索应用或分享入口");
        search.setContentDescription("搜索分享入口");
        search.setBackgroundColor(Color.TRANSPARENT);
        search.setPadding(dp(8), dp(6), 0, dp(6));
        search.setImeOptions(EditorInfo.IME_ACTION_DONE);
        search.setOnEditorActionListener((view, action, event) -> {
            if (action != EditorInfo.IME_ACTION_DONE) return false;
            InputMethodManager keyboard = activity.getSystemService(InputMethodManager.class);
            if (keyboard != null) keyboard.hideSoftInputFromWindow(search.getWindowToken(), 0);
            search.clearFocus();
            return true;
        });
        searchRow.addView(search, new LinearLayout.LayoutParams(0, -2, 1));
        clearSearch = iconButton(R.drawable.ic_share_clear, "清空搜索");
        clearSearch.setVisibility(View.INVISIBLE);
        activity.setTapClickListener(clearSearch, v -> search.setText(""));
        searchRow.addView(clearSearch, new LinearLayout.LayoutParams(dp(36), dp(36)));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                clearSearch.setVisibility(s.length() == 0 ? View.INVISIBLE : View.VISIBLE);
                render();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        root.addView(searchRow, activity.matchWrapWithTop(6));

        LinearLayout filterBar = row();
        LinearLayout filterTabs = row();
        filterTabs.setBackground(activity.roundRect(activity.surfaceSoftColor(), 10));
        filterTabs.setPadding(dp(2), dp(2), dp(2), dp(2));

        shownTab = button("显示中 0", false);
        hiddenTab = button("已隐藏 0", false);
        shownTab.setContentDescription("查看显示中的入口");
        hiddenTab.setContentDescription("查看已隐藏的入口");
        shownTab.setMinHeight(dp(30));
        hiddenTab.setMinHeight(dp(30));
        shownTab.setTextSize(12);
        hiddenTab.setTextSize(12);
        shownTab.setPadding(dp(10), dp(4), dp(10), dp(4));
        hiddenTab.setPadding(dp(10), dp(4), dp(10), dp(4));
        activity.setTapClickListener(shownTab, v -> selectVisibility(false));
        activity.setTapClickListener(hiddenTab, v -> selectVisibility(true));
        filterTabs.addView(shownTab);
        filterTabs.addView(hiddenTab);
        filterBar.addView(filterTabs);

        status = text("正在扫描分享入口…", 11, activity.subtextColor());
        status.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        status.setSingleLine(true);
        status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        status.setPadding(dp(8), 0, dp(4), 0);
        filterBar.addView(status, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(filterBar, activity.matchWrapWithTop(6));

        FrameLayout contentArea = new FrameLayout(activity);
        FrameLayout gridArea = new FrameLayout(activity);
        list = new GridView(activity);
        list.setNumColumns(4);
        list.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        list.setHorizontalSpacing(dp(4));
        list.setVerticalSpacing(dp(6));
        list.setClipToPadding(false);
        list.setPadding(0, dp(4), 0, dp(56));
        list.setSelector(new ColorDrawable(Color.TRANSPARENT));
        list.setAdapter(adapter);
        list.setOnDragListener(this::onDrag);
        gridArea.addView(list, new FrameLayout.LayoutParams(-1, -1));
        empty = text("", 14, activity.subtextColor());
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(24), dp(16), dp(24), dp(16));
        empty.setVisibility(View.GONE);
        gridArea.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        contentArea.addView(gridArea, new FrameLayout.LayoutParams(-1, -1));

        undoBar = row();
        undoBar.setPadding(dp(14), dp(4), dp(6), dp(4));
        undoBar.setBackground(activity.roundRect(activity.surfaceSoftColor(), 20));
        undoBar.setElevation(dp(6));
        undoMessage = text("", 12, activity.textColor());
        undoMessage.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        undoBar.addView(undoMessage, new LinearLayout.LayoutParams(-2, -2));
        TextView undo = button("撤销", false);
        undo.setMinHeight(dp(36));
        undo.setTextSize(12);
        undo.setPadding(dp(10), dp(4), dp(10), dp(4));
        undo.setTextColor(activity.primaryColor());
        activity.setTapClickListener(undo, v -> {
            Runnable action = undoAction;
            dismissUndo();
            if (action != null) action.run();
        });
        undoBar.addView(undo);
        undoBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams undoLp = new FrameLayout.LayoutParams(-2, -2);
        undoLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        undoLp.bottomMargin = dp(12);
        contentArea.addView(undoBar, undoLp);

        root.addView(contentArea, new LinearLayout.LayoutParams(-1, 0, 1));

        overlayRoot = new FrameLayout(activity);
        overlayRoot.addView(root, new FrameLayout.LayoutParams(-1, -1));
        sheetScrim = new View(activity);
        sheetScrim.setBackgroundColor(Color.argb(77, 0, 0, 0));
        sheetScrim.setVisibility(View.GONE);
        sheetScrim.setOnClickListener(v -> dismissSheet());
        overlayRoot.addView(sheetScrim, new FrameLayout.LayoutParams(-1, -1));
        dialog.setContentView(overlayRoot);
        dialog.setOnDismissListener(ignored -> {
            closed = true;
            clearDrag();
            dismissUndo();
            dismissSheet();
            if (appManager != null) appManager.dismiss();
            if (exitPrompt != null) exitPrompt.dismiss();
        });
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(activity.surfaceColor()));
            window.setLayout(-1, -1);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            if (Build.VERSION.SDK_INT >= 33) {
                window.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                        OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::requestClose);
            }
        }
        load();
    }

    private void selectVisibility(boolean hidden) {
        if (loading || showHidden == hidden) return;
        clearDrag();
        showHidden = hidden;
        render();
        list.setSelection(0);
    }

    private void updateTabs() {
        for (Map.Entry<ShareContentType, TextView> entry : typeTabs.entrySet()) {
            styleTab(entry.getValue(), entry.getKey() == selected);
            entry.getValue().setEnabled(!loading && !catalogs.isEmpty());
        }
        styleTab(shownTab, !showHidden);
        styleTab(hiddenTab, showHidden);
        shownTab.setEnabled(!loading && draft != null);
        hiddenTab.setEnabled(!loading && draft != null);
        if (byApp != null) {
            boolean ready = !loading && draft != null && !targets.isEmpty();
            byApp.setEnabled(ready);
            byApp.setAlpha(ready ? 1f : 0.4f);
        }
    }

    private void styleTab(TextView view, boolean active) {
        view.setSelected(active);
        view.setTextColor(active ? activity.primaryColor() : activity.subtextColor());
        view.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
        boolean isFilter = view == shownTab || view == hiddenTab;
        if (isFilter) {
            view.setBackground(activity.roundRect(active ? activity.surfaceColor() : Color.TRANSPARENT, 8));
        } else {
            view.setBackground(activity.roundRect(active ? activity.surfaceSoftColor() : Color.TRANSPARENT, 10));
        }
    }

    private void load() {
        if (loading) return;
        stash();
        // Invalidate all MIME snapshots and shared resources on explicit rescan.
        ShareTargetCatalog.Snapshot previous = catalogs.get(selected);
        catalogs.clear();
        if (previous != null) catalogs.put(selected, previous);
        scanSession = new ShareTargetCatalog.Session(() -> closed);
        scanSelected();
    }

    private void scanSelected() {
        dismissUndo();
        clearDrag();
        loading = true;
        independent.setEnabled(false);
        updateTabs();
        updateSaveState();
        adapter.notifyDataSetChanged();
        status.setText("正在扫描分享入口…");
        empty.setText("正在加载分享入口…");
        empty.setVisibility(draft == null ? View.VISIBLE : View.GONE);
        android.content.Context context = activity.getApplicationContext();
        ShareContentType requested = selected;
        ShareTargetCatalog.Session session = scanSession;
        scanner.execute(() -> {
            if (closed) return;
            try {
                ShareTargetCatalog.Snapshot found = catalog == null
                        ? session.load(context, requested) : catalog.apply(context, requested);
                activity.runOnUiThread(() -> {
                    if (closed || activity.isFinishing() || activity.isDestroyed()) return;
                    catalogs.put(requested, found);
                    loading = false;
                    selectDraft();
                });
            } catch (RuntimeException error) {
                activity.runOnUiThread(() -> {
                    if (closed || activity.isFinishing() || activity.isDestroyed()) return;
                    loading = false;
                    independent.setEnabled(draft != null);
                    if (draft != null) render();
                    updateTabs();
                    updateSaveState();
                    status.setText("扫描失败，可在更多中重新扫描");
                    if (draft == null) empty.setText("暂时无法加载分享入口\n已保存的设置仍保留");
                });
            }
        });
    }

    private void save() {
        if (draft == null || loading) return;
        stash();
        ShareTargetRules rules = defaults;
        activity.prefs().edit()
                .putString(SettingsStore.KEY_SHARE_TARGET_PROFILES, profiles.encode())
                .putString(SettingsStore.KEY_SHARE_TARGET_ORDER, rules.encodeOrder())
                .putString(SettingsStore.KEY_SHARE_HIDDEN_TARGETS, rules.encodeHidden()).apply();
        SettingsStore.notifyChanged(activity);
        activity.showToast("已保存，重新打开分享面板生效");
        dialog.dismiss();
    }

    private void stash() {
        if (draft == null) return;
        if (selected == ShareContentType.DEFAULT) defaults = draft.rules();
        else if (profiles.hasOverride(selected)) profiles.set(selected, draft.rules());
    }

    private boolean editable() {
        return !loading && (selected == ShareContentType.DEFAULT || profiles.hasOverride(selected));
    }

    private void selectDraft() {
        clearDrag();
        targets.clear();
        for (ShareTargetCatalog.Target target : catalogs.get(selected).targets) targets.put(target.component, target);
        draft = new ShareTargetsDraft(targets.keySet(), profiles.rulesFor(selected, defaults));
        updatingSelection = true;
        independent.setEnabled(true);
        profileRow.setVisibility(selected == ShareContentType.DEFAULT ? View.GONE : View.VISIBLE);
        independent.setChecked(profiles.hasOverride(selected));
        profileStatus.setText(profiles.hasOverride(selected) ? "独立配置" : "沿用默认");
        updatingSelection = false;
        search.setText("");
        render();
        list.setSelection(0);
    }

    private void render() {
        if (draft == null || status == null || loading) return;
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        visible.clear();
        int hiddenCount = 0;
        for (String component : draft.components()) {
            if (draft.isHidden(component)) hiddenCount++;
            if (draft.isHidden(component) != showHidden) continue;
            ShareTargetCatalog.Target target = target(component);
            String haystack = component + (target == null ? "" : " " + target.label + " " + target.appName);
            if (haystack.toLowerCase(Locale.ROOT).contains(query)) visible.add(component);
        }
        shownTab.setText("显示中 " + (draft.components().size() - hiddenCount));
        hiddenTab.setText("已隐藏 " + hiddenCount);
        updateTabs();
        String hint = !editable() ? "沿用默认 · 开启独立后可调"
                : !query.isEmpty() ? "已找到 " + visible.size() + " 个"
                : showHidden ? "点击入口恢复显示"
                : (draft.hasFixedOrder() ? "自定义排序" : "系统排序") + " · 长按拖动";
        if (!catalogs.get(selected).systemOrderAvailable && !draft.hasFixedOrder()) {
            hint = "系统查询顺序 · 长按拖动";
        }
        status.setText(hint);
        empty.setText(!query.isEmpty() ? "没有找到匹配的入口\n试试应用名称，或清空搜索"
                : showHidden ? "没有隐藏的入口\n在显示中点击入口即可隐藏"
                : "没有显示中的入口\n" + (hiddenCount > 0 ? "可前往已隐藏恢复入口" : "可在更多中重新扫描"));
        empty.setVisibility(visible.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
        updateSaveState();
    }

    private String state() {
        // Capture every profile, including the draft of the currently selected type.
        stash();
        StringBuilder result = new StringBuilder();
        appendState(result, defaults);
        for (ShareContentType type : ShareContentType.values()) {
            if (profiles.hasOverride(type)) {
                result.append('\n').append(type.name());
                appendState(result, profiles.rulesFor(type, defaults));
            }
        }
        return result.toString();
    }

    private void appendState(StringBuilder result, ShareTargetRules rules) {
        // Hidden rules are a set: restoring/undoing an entry must not create a false change.
        result.append("\n--order--\n").append(rules.encodeOrder())
                .append("\n--hidden--\n").append(String.join("\n", new TreeSet<>(rules.hidden())));
    }

    private boolean dirty() { return initialState != null && !initialState.equals(state()); }

    private void updateSaveState() {
        boolean changed = dirty();
        changes.setText(changed ? "未保存 · 保存全部修改" : "一次保存所有类型");
        changes.setTextColor(changed ? activity.primaryColor() : activity.subtextColor());
        save.setEnabled(draft != null && !loading && changed);
        save.setAlpha(save.isEnabled() ? 1f : 0.4f);
        if (exitPrompt != null && exitPrompt.isShowing()) {
            exitPrompt.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(draft != null && !loading);
        }
    }

    private void requestClose() {
        if (sheetBody != null) {
            dismissSheet();
            return;
        }
        if (closed || (exitPrompt != null && exitPrompt.isShowing())) return;
        if (!dirty()) { dialog.dismiss(); return; }
        exitPrompt = new AlertDialog.Builder(activity).setTitle("保存分享列表修改？")
                .setMessage("所有类型的修改尚未保存。")
                .setPositiveButton("保存", (d, which) -> save())
                .setNegativeButton("放弃修改", (d, which) -> dialog.dismiss())
                .setNeutralButton("继续编辑", null).create();
        exitPrompt.show();
        updateSaveState();
    }

    private boolean canReorder() {
        if (draft == null || !editable() || showHidden) return false;
        if (!search.getText().toString().trim().isEmpty()) {
            activity.showToast("请先清空搜索，再调整顺序");
            return false;
        }
        return true;
    }

    private void showTargetMenu(String component) {
        if (draft == null || loading) return;
        ShareTargetCatalog.Target target = target(component);
        boolean hidden = draft.isHidden(component);
        LinearLayout body = newSheet(targetName(component, target), appName(target));
        if (!editable()) {
            body.addView(text("此类型沿用默认配置，开启单独设置后可编辑。", 14, activity.subtextColor()),
                    activity.matchWrapWithTop(8));
            sheetAction(body, "单独设置此类型", true, () -> independent.setChecked(true));
        } else {
            sheetAction(body, hidden ? "恢复显示" : "隐藏此入口", true,
                    () -> setHidden(component, !hidden));
            if (!hidden) {
                boolean sortable = search.getText().toString().trim().isEmpty();
                int index = visible.indexOf(component);
                sheetAction(body, "移到顶部", sortable && index > 0, () -> moveInVisible(component, 0));
                sheetAction(body, "向上移动", sortable && index > 0, () -> moveInVisible(component, 1));
                sheetAction(body, "向下移动", sortable && index >= 0 && index < visible.size() - 1,
                        () -> moveInVisible(component, 2));
                sheetAction(body, "移到底部", sortable && index >= 0 && index < visible.size() - 1,
                        () -> moveInVisible(component, 3));
                body.addView(text(sortable ? "排序仅在系统的常用、更多和设备各组内生效" : "清空搜索后可调整顺序",
                        12, activity.subtextColor()), activity.matchWrapWithTop(8));
            }
        }
        showSheet(body);
    }

    private void moveInVisible(String component, int action) {
        if (!canReorder()) return;
        int from = visible.indexOf(component);
        if (from < 0) return;
        int to = action == 0 ? 0 : action == 1 ? Math.max(0, from - 1)
                : action == 2 ? Math.min(visible.size() - 1, from + 1) : visible.size() - 1;
        // Hidden targets are absent from the grid; map the visible neighbor back to the full draft.
        move(component, draft.components().indexOf(visible.get(to)));
    }

    private void setHidden(String component, boolean hidden) {
        if (!editable()) return;
        boolean before = draft.isHidden(component);
        if (before == hidden) return;
        dismissUndo();
        ShareTargetsDraft edited = draft;
        draft.setHidden(component, hidden);
        render();
        showUndo(hidden ? "已隐藏此入口" : "已恢复显示", () -> {
            if (draft != edited || !editable()) return;
            draft.setHidden(component, before);
            render();
        });
    }

    private void showUndo(String message, Runnable action) {
        undoAction = action;
        undoMessage.setText(message);
        undoBar.setVisibility(View.VISIBLE);
        AccessibilityManager accessibility = activity.getSystemService(AccessibilityManager.class);
        int timeout = 6000;
        if (Build.VERSION.SDK_INT >= 29 && accessibility != null) {
            timeout = accessibility.getRecommendedTimeoutMillis(timeout,
                    AccessibilityManager.FLAG_CONTENT_TEXT | AccessibilityManager.FLAG_CONTENT_CONTROLS);
        } else if (accessibility != null && accessibility.isTouchExplorationEnabled()) {
            timeout = 15000;
        }
        undoBar.postDelayed(clearUndo, timeout);
    }

    private void dismissUndo() {
        if (undoBar == null) return;
        undoBar.removeCallbacks(clearUndo);
        undoBar.setVisibility(View.GONE);
        undoAction = null;
    }

    private void openAppManager() {
        if (loading || draft == null || targets.isEmpty()) return;
        dismissUndo();
        clearDrag();
        if (!editable()) {
            new AlertDialog.Builder(activity)
                    .setTitle("开启单独设置？")
                    .setMessage("此类型沿用默认配置，开启单独设置后可按应用管理。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("开启并管理", (d, which) -> {
                        independent.setChecked(true);
                        showAppManager();
                    }).show();
            return;
        }
        showAppManager();
    }

    private void showAppManager() {
        if ((appManager != null && appManager.isShowing()) || !editable()) return;
        appManager = new ShareAppManagerDialog(activity, selected, targets.values(), draft, this::render).show();
    }

    private void showMoreMenu() {
        LinearLayout body = newSheet("分享列表选项", "当前类型：" + selected.label);
        sheetAction(body, "重新扫描", !loading, this::load);
        sheetAction(body, "恢复系统排序", draft != null && editable() && draft.hasFixedOrder(), () -> {
            dismissUndo();
            draft.followSystemOrder();
            search.setText("");
            render();
            list.setSelection(0);
            activity.showToast("已恢复系统排序，隐藏设置保留，点击保存生效");
        });
        body.addView(text("恢复系统排序会保留隐藏设置", 12, activity.subtextColor()));
        sheetAction(body, "重置当前类型", draft != null && !loading, () -> {
            new AlertDialog.Builder(activity).setTitle("重置" + selected.label + "配置？")
                    .setMessage(selected == ShareContentType.DEFAULT
                            ? "清除默认配置的排序和隐藏设置，沿用默认的类型也会同步变化。独立配置与总开关不受影响。点击保存后生效。"
                            : "此类型将重新沿用默认配置，其他类型与总开关不受影响。点击保存后生效。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("重置", (d, which) -> resetSelected()).show();
        });
        sheetAction(body, "使用说明", true, () -> new AlertDialog.Builder(activity)
                .setTitle("分享列表使用说明")
                .setMessage("长按图标拖动，或点击入口调整位置、隐藏入口。已隐藏的入口可在“已隐藏”中恢复。\n\n"
                        + "默认跟随系统排序，仅隐藏入口不会固定顺序。实际调整顺序会自动启用自定义列表。"
                        + "排序只在 Flyme 的常用、更多和设备各组内生效。\n\n"
                        + "未单独设置的类型沿用默认，混合或未识别类型使用默认配置。"
                        + "各类型只列出支持该内容的入口，单文件和多文件共用配置。\n\n"
                        + "保存会一次应用所有类型的修改，重新打开分享面板生效。"
                        + "首次使用请在 LSPosed 中启用系统分享范围。"
                        + "Flyme 图库内分享还需勾选图库（com.meizu.media.gallery），并重启对应应用或手机。")
                .setPositiveButton("知道了", null).show());
        showSheet(body);
    }

    private void resetSelected() {
        if (draft == null || loading) return;
        dismissUndo();
        if (selected == ShareContentType.DEFAULT) {
            draft.reset();
            defaults = draft.rules();
        } else {
            profiles.set(selected, null);
            selectDraft();
        }
        showHidden = false;
        search.setText("");
        render();
        list.setSelection(0);
    }

    private LinearLayout newSheet(String title, String subtitle) {
        dismissSheet();
        LinearLayout body = column();
        body.setPadding(dp(20), dp(12), dp(20), dp(16));
        body.setBackground(activity.roundRect(activity.surfaceColor(), 24));
        View handle = new View(activity);
        handle.setBackground(activity.roundRect(activity.strokeColor(), 2));
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(dp(32), dp(4));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.bottomMargin = dp(18);
        body.addView(handle, handleLp);
        TextView heading = text(title, 20, activity.textColor());
        heading.setTypeface(null, Typeface.BOLD);
        body.addView(heading);
        body.addView(text(subtitle, 13, activity.subtextColor()), activity.matchWrapWithTop(4));
        return body;
    }

    private void sheetAction(LinearLayout body, String label, boolean available, Runnable action) {
        TextView item = text(label, 16, activity.textColor());
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setMinHeight(dp(52));
        item.setPadding(dp(12), dp(8), dp(12), dp(8));
        item.setBackground(activity.roundRect(activity.surfaceSoftColor(), 14));
        item.setEnabled(available);
        item.setAlpha(available ? 1f : 0.4f);
        activity.setTapClickListener(item, v -> { dismissSheet(); action.run(); });
        body.addView(item, activity.matchWrapWithTop(8));
    }

    private void showSheet(LinearLayout body) {
        TextView cancel = button("取消", false);
        cancel.setMinHeight(dp(48));
        activity.setTapClickListener(cancel, v -> dismissSheet());
        body.addView(cancel, activity.matchWrapWithTop(12));
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.addView(body);
        sheetBody = body;
        FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(
                Math.min(activity.getResources().getDisplayMetrics().widthPixels, dp(560)), -2,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        overlayRoot.addView(scroll, contentParams);
        sheetScrim.setVisibility(View.VISIBLE);
    }

    private void dismissSheet() {
        if (overlayRoot == null) return;
        if (sheetBody != null) {
            View sheet = overlayRoot.getChildAt(overlayRoot.getChildCount() - 1);
            if (sheet != sheetScrim) overlayRoot.removeView(sheet);
            sheetBody = null;
        }
        if (sheetScrim != null) sheetScrim.setVisibility(View.GONE);
    }

    private boolean move(String component, int destination) {
        if (!canReorder() || !draft.move(component, destination)) return false;
        dismissUndo();
        render();
        list.setSelection(Math.max(0, visible.indexOf(component)));
        return true;
    }

    private boolean startDrag(View handle, String component) {
        if (!canReorder()) return false;
        dragComponent = component;
        boolean started = handle.startDragAndDrop(ClipData.newPlainText("share-target", component),
                new View.DragShadowBuilder(handle), this, 0);
        if (started) {
            activity.performTapHaptic(handle);
            handle.animate().scaleX(1.06f).scaleY(1.06f).translationZ(dp(8)).setDuration(120).start();
            handle.setAlpha(0.5f);
        } else dragComponent = null;
        return started;
    }

    private boolean onDrag(View view, DragEvent event) {
        if (event.getLocalState() != this) return false;
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return dragComponent != null;
            case DragEvent.ACTION_DRAG_LOCATION:
                dragX = event.getX();
                dragY = event.getY();
                updateDropTarget();
                list.removeCallbacks(dragScroll);
                list.postOnAnimation(dragScroll);
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
                dropComponent = null;
                list.removeCallbacks(dragScroll);
                adapter.notifyDataSetChanged();
                return true;
            case DragEvent.ACTION_DROP:
                dragX = event.getX();
                dragY = event.getY();
                updateDropTarget();
                if (dropComponent == null || dragComponent == null) return false;
                int destination = draft.components().indexOf(dropComponent);
                return move(dragComponent, destination);
            case DragEvent.ACTION_DRAG_ENDED:
                clearDrag();
                adapter.notifyDataSetChanged();
                return true;
            default: return true;
        }
    }

    private void updateDropTarget() {
        int position = list.pointToPosition((int) dragX, (int) dragY);
        // Use the nearest visible cell in grid gaps and while scrolling at an edge.
        if (position == GridView.INVALID_POSITION) {
            float nearest = Float.MAX_VALUE;
            for (int i = 0; i < list.getChildCount(); i++) {
                View cell = list.getChildAt(i);
                float dx = dragX - (cell.getLeft() + cell.getRight()) / 2f;
                float dy = dragY - (cell.getTop() + cell.getBottom()) / 2f;
                float distance = dx * dx + dy * dy;
                if (distance < nearest) {
                    nearest = distance;
                    position = list.getFirstVisiblePosition() + i;
                }
            }
        }
        String next = position >= 0 && position < visible.size() ? visible.get(position) : null;
        if (!java.util.Objects.equals(next, dropComponent)) {
            dropComponent = next;
            adapter.notifyDataSetChanged();
        }
    }

    private final Runnable dragScroll = new Runnable() {
        @Override public void run() {
            if (closed || dragComponent == null) return;
            int direction = dragY < dp(56) ? -1 : dragY > list.getHeight() - dp(56) ? 1 : 0;
            if (direction == 0 || !list.canScrollList(direction)) return;
            list.scrollListBy(direction * dp(7));
            updateDropTarget();
            list.postOnAnimation(this);
        }
    };

    private void clearDrag() {
        dragComponent = null;
        dropComponent = null;
        if (list == null) return;
        list.removeCallbacks(dragScroll);
        for (int i = 0; i < list.getChildCount(); i++) {
            View cell = list.getChildAt(i);
            cell.animate().cancel();
            cell.setScaleX(1f);
            cell.setScaleY(1f);
            cell.setTranslationZ(0);
            cell.setAlpha(1f);
        }
    }

    private ShareTargetCatalog.Target target(String component) {
        ShareTargetCatalog.Target target = targets.get(component);
        if (target == null && catalogs.containsKey(ShareContentType.DEFAULT)) {
            for (ShareTargetCatalog.Target candidate : catalogs.get(ShareContentType.DEFAULT).targets) {
                if (candidate.component.equals(component)) return candidate;
            }
        }
        return target;
    }

    static String targetName(String component, ShareTargetCatalog.Target target) {
        if (target == null) return "不可用入口";
        String label = target.label;
        return label.equals(component) || label.equals(component.substring(component.indexOf('/') + 1))
                ? "分享入口" : label;
    }

    private String appName(ShareTargetCatalog.Target target) {
        if (target == null) return "未检测到应用";
        return target.appName.equals(target.component.substring(0, target.component.indexOf('/')))
                ? "应用" : target.appName;
    }

    private final class TargetAdapter extends BaseAdapter {
        @Override public int getCount() { return visible.size(); }
        @Override public Object getItem(int position) { return visible.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            String component = visible.get(position);
            ShareTargetCatalog.Target target = target(component);
            TargetCell cell = convertView instanceof TargetCell ? (TargetCell) convertView : new TargetCell();
            String name = targetName(component, target);
            String app = appName(target);
            cell.animate().cancel();
            boolean dragging = component.equals(dragComponent);
            boolean drop = component.equals(dropComponent) && !dragging;
            cell.setScaleX(dragging ? 1.06f : 1f);
            cell.setScaleY(dragging ? 1.06f : 1f);
            cell.setTranslationZ(dragging ? dp(8) : 0);
            cell.setAlpha(dragging ? 0.5f : 1f);
            cell.setBackground(drop
                    ? activity.outlinedRect(activity.surfaceSoftColor(), activity.primaryColor(), 2, 16)
                    : activity.roundRect(Color.TRANSPARENT, 16));
            cell.icon.setImageDrawable(target == null || target.icon == null
                    ? activity.getPackageManager().getDefaultActivityIcon() : target.icon);
            boolean isHidden = draft.isHidden(component);
            cell.icon.setAlpha(isHidden ? 0.35f : 1f);
            cell.function.setAlpha(isHidden ? 0.45f : 1f);
            cell.app.setAlpha(isHidden ? 0.45f : 1f);
            cell.function.setText(name);
            cell.app.setText(app);
            cell.setContentDescription(name + "，" + app + (isHidden
                    ? "，已隐藏，点击管理" : "，点击管理，长按拖动排序"));
            cell.setEnabled(!loading);
            activity.setTapClickListener(cell, v -> showTargetMenu(component));
            cell.setOnLongClickListener(v -> startDrag(v, component));
            cell.setLongClickable(editable() && !showHidden && search.getText().toString().trim().isEmpty());
            return cell;
        }
    }

    private final class TargetCell extends LinearLayout {
        final ImageView icon;
        final TextView function;
        final TextView app;

        TargetCell() {
            super(activity);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_HORIZONTAL);
            setPadding(dp(2), dp(8), dp(2), dp(6));
            setFocusable(true);
            icon = new ImageView(activity);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));
            function = text("", 12, activity.textColor());
            function.setGravity(Gravity.CENTER);
            function.setSingleLine(true);
            function.setEllipsize(android.text.TextUtils.TruncateAt.END);
            function.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(function, activity.matchWrapWithTop(4));
            app = text("", 11, activity.subtextColor());
            app.setGravity(Gravity.CENTER);
            app.setSingleLine(true);
            app.setEllipsize(android.text.TextUtils.TruncateAt.END);
            app.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(app, activity.matchWrapWithTop(1));
        }
    }

    private LinearLayout column() {
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    private ImageView iconButton(int resource, String description) {
        ImageView view = new ImageView(activity);
        view.setImageResource(resource);
        view.setColorFilter(activity.primaryColor());
        view.setPadding(dp(12), dp(12), dp(12), dp(12));
        view.setBackground(activity.roundRect(Color.TRANSPARENT, 16));
        view.setContentDescription(description);
        view.setFocusable(true);
        return view;
    }

    private int dp(int value) { return activity.dp(value); }
    private LinearLayout row() {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }
    private TextView text(String label, int size, int color) {
        TextView view = new TextView(activity);
        view.setText(label);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }
    private TextView button(String label, boolean primary) {
        return activity.filledButton(label, primary ? activity.primaryColor() : activity.surfaceSoftColor(),
                primary ? Color.WHITE : activity.textColor());
    }
}
