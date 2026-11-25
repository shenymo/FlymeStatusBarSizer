package com.example.flymestatusbarsizer.feature.share;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.window.OnBackInvokedDispatcher;

import com.example.flymestatusbarsizer.MainActivity;
import com.example.flymestatusbarsizer.R;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** App-grouped view for batch hiding or toggling individual share targets. */
final class ShareAppManagerDialog {
    private final MainActivity activity;
    private final ShareContentType type;
    private final ShareTargetsDraft draft;
    private final Runnable onChanged;
    private final List<AppGroup> allGroups;
    private final List<AppGroup> visibleGroups = new ArrayList<>();
    private final Set<String> expanded = new HashSet<>();
    private final GroupAdapter adapter = new GroupAdapter();
    private Dialog dialog;
    private EditText search;
    private ImageView clearSearch;
    private TextView subtitle;
    private TextView empty;

    ShareAppManagerDialog(MainActivity activity, ShareContentType type,
            Collection<ShareTargetCatalog.Target> targets, ShareTargetsDraft draft, Runnable onChanged) {
        this.activity = activity;
        this.type = type;
        this.draft = draft;
        this.onChanged = onChanged;
        this.allGroups = buildGroups(targets);
    }

    Dialog show() {
        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(false);

        LinearLayout root = column();
        root.setFitsSystemWindows(true);
        root.setPadding(dp(16), dp(4), dp(16), 0);
        root.setBackgroundColor(activity.surfaceColor());

        LinearLayout titleRow = row();
        ImageView back = iconButton(R.drawable.ic_settings_back, "返回");
        activity.setTapClickListener(back, v -> dialog.dismiss());
        titleRow.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout heading = column();
        TextView title = text("按应用管理", 20, activity.textColor());
        title.setTypeface(null, Typeface.BOLD);
        heading.addView(title);
        subtitle = text("", 11, activity.subtextColor());
        heading.addView(subtitle);
        titleRow.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(titleRow, activity.matchWrap());

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
                filter();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        root.addView(searchRow, activity.matchWrapWithTop(6));

        FrameLayout content = new FrameLayout(activity);
        ListView list = new ListView(activity);
        list.setDivider(new ColorDrawable(Color.TRANSPARENT));
        list.setDividerHeight(dp(8));
        list.setClipToPadding(false);
        list.setPadding(0, dp(8), 0, dp(24));
        list.setSelector(new ColorDrawable(Color.TRANSPARENT));
        list.setAdapter(adapter);
        content.addView(list, new FrameLayout.LayoutParams(-1, -1));

        empty = text("没有找到匹配的应用", 14, activity.subtextColor());
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(24), dp(16), dp(24), dp(16));
        empty.setVisibility(View.GONE);
        content.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        dialog.setContentView(root);
        dialog.setOnDismissListener(ignored -> onChanged.run());
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(activity.surfaceColor()));
            window.setLayout(-1, -1);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            if (Build.VERSION.SDK_INT >= 33) {
                window.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                        OnBackInvokedDispatcher.PRIORITY_DEFAULT, dialog::dismiss);
            }
        }
        filter();
        return dialog;
    }

    private List<AppGroup> buildGroups(Collection<ShareTargetCatalog.Target> targets) {
        Map<String, AppGroup> byPackage = new LinkedHashMap<>();
        Drawable fallbackIcon = activity.getPackageManager().getDefaultActivityIcon();
        for (ShareTargetCatalog.Target target : targets) {
            int slash = target.component.indexOf('/');
            if (slash <= 0) continue;
            String pkg = target.component.substring(0, slash);
            AppGroup group = byPackage.get(pkg);
            if (group == null) {
                String name = target.appName.equals(pkg) ? "应用" : target.appName;
                Drawable appIcon = target.appIcon != null ? target.appIcon
                        : target.icon != null ? target.icon : fallbackIcon;
                group = new AppGroup(pkg, name, appIcon);
                byPackage.put(pkg, group);
            }
            group.entries.add(new Entry(target.component, ShareTargetsEditor.targetName(target.component, target),
                    target.icon != null ? target.icon : fallbackIcon));
        }
        // Freeze initial order: active apps first, fully hidden apps last, no jumping while toggling.
        List<AppGroup> active = new ArrayList<>();
        List<AppGroup> disabled = new ArrayList<>();
        for (AppGroup group : byPackage.values()) {
            if (shownCount(group) > 0) active.add(group); else disabled.add(group);
        }
        active.addAll(disabled);
        return active;
    }

    private void filter() {
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        visibleGroups.clear();
        for (AppGroup group : allGroups) {
            if (query.isEmpty() || matchesGroup(group, query)) visibleGroups.add(group);
        }
        empty.setVisibility(visibleGroups.isEmpty() ? View.VISIBLE : View.GONE);
        updateSubtitle();
        adapter.notifyDataSetChanged();
    }

    private boolean matchesGroup(AppGroup group, String query) {
        if (group.appName.toLowerCase(Locale.ROOT).contains(query)
                || group.packageName.toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        for (Entry entry : group.entries) {
            if (entry.label.toLowerCase(Locale.ROOT).contains(query)) return true;
        }
        return false;
    }

    private boolean matchesEntryOnly(AppGroup group, String query) {
        if (query.isEmpty()) return false;
        if (group.appName.toLowerCase(Locale.ROOT).contains(query)
                || group.packageName.toLowerCase(Locale.ROOT).contains(query)) {
            return false;
        }
        for (Entry entry : group.entries) {
            if (entry.label.toLowerCase(Locale.ROOT).contains(query)) return true;
        }
        return false;
    }

    private int shownCount(AppGroup group) {
        int shown = 0;
        for (Entry entry : group.entries) {
            if (!draft.isHidden(entry.component)) shown++;
        }
        return shown;
    }

    private void updateSubtitle() {
        int totalEntries = 0;
        int hiddenEntries = 0;
        for (AppGroup group : allGroups) {
            totalEntries += group.entries.size();
            for (Entry entry : group.entries) {
                if (draft.isHidden(entry.component)) hiddenEntries++;
            }
        }
        String typeLabel = type == ShareContentType.DEFAULT ? "默认" : type.label;
        subtitle.setText(typeLabel + " · 已隐藏 " + hiddenEntries + "/" + totalEntries);
    }

    private void applyChanges() {
        updateSubtitle();
        adapter.notifyDataSetChanged();
        onChanged.run();
    }

    private final class GroupAdapter extends BaseAdapter {
        @Override public int getCount() { return visibleGroups.size(); }
        @Override public Object getItem(int position) { return visibleGroups.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            AppGroup group = visibleGroups.get(position);
            AppCard card = convertView instanceof AppCard ? (AppCard) convertView : new AppCard();
            String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
            boolean autoExpanded = group.entries.size() > 1 && matchesEntryOnly(group, query);
            boolean isExpanded = group.entries.size() > 1
                    && (autoExpanded || expanded.contains(group.packageName));
            card.bind(group, isExpanded);
            return card;
        }
    }

    private final class AppCard extends LinearLayout {
        private final LinearLayout header;
        private final ImageView icon;
        private final TextView name;
        private final TextView summary;
        private final ImageView chevron;
        private final Switch toggle;
        private final LinearLayout childrenContainer;
        private final View divider;
        private final LinearLayout childrenList;

        AppCard() {
            super(activity);
            setOrientation(VERTICAL);
            setPadding(dp(14), dp(8), dp(10), dp(8));
            setBackground(activity.roundRect(activity.surfaceSoftColor(), 16));

            header = row();
            header.setMinimumHeight(dp(48));
            icon = new ImageView(activity);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            header.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));

            LinearLayout labels = column();
            name = text("", 15, activity.textColor());
            name.setTypeface(null, Typeface.BOLD);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            labels.addView(name);
            summary = text("", 12, activity.subtextColor());
            summary.setSingleLine(true);
            summary.setEllipsize(TextUtils.TruncateAt.END);
            labels.addView(summary, activity.matchWrapWithTop(2));
            LinearLayout.LayoutParams labelsLp = new LinearLayout.LayoutParams(0, -2, 1);
            labelsLp.setMarginStart(dp(12));
            labelsLp.setMarginEnd(dp(4));
            header.addView(labels, labelsLp);

            chevron = new ImageView(activity);
            chevron.setImageResource(R.drawable.ic_section_expand);
            chevron.setPadding(dp(6), dp(6), dp(6), dp(6));
            chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            header.addView(chevron, new LinearLayout.LayoutParams(dp(28), dp(28)));

            toggle = new Switch(activity);
            activity.styleSwitch(toggle);
            header.addView(toggle);
            addView(header, activity.matchWrap());

            childrenContainer = column();
            divider = new View(activity);
            divider.setBackgroundColor(activity.strokeColor());
            LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(-1, Math.max(1, dp(1)));
            divLp.topMargin = dp(6);
            divLp.bottomMargin = dp(4);
            childrenContainer.addView(divider, divLp);

            childrenList = column();
            childrenContainer.addView(childrenList, activity.matchWrap());
            addView(childrenContainer, activity.matchWrap());
        }

        void bind(AppGroup group, boolean isExpanded) {
            int total = group.entries.size();
            int shown = shownCount(group);
            boolean allHidden = shown == 0;

            icon.setImageDrawable(group.appIcon);
            icon.setAlpha(allHidden ? 0.4f : 1f);
            name.setText(group.appName);
            name.setAlpha(allHidden ? 0.5f : 1f);
            summary.setText(summaryText(group, shown, total));
            summary.setAlpha(allHidden ? 0.6f : 1f);

            chevron.setVisibility(total > 1 ? View.VISIBLE : View.GONE);
            chevron.setRotation(isExpanded ? 180f : 0f);

            toggle.setOnCheckedChangeListener(null);
            toggle.setChecked(!allHidden);
            toggle.setContentDescription(group.appName + "，分享开关");
            toggle.setOnCheckedChangeListener((view, checked) -> {
                draft.setHiddenAll(group.components(), !checked);
                applyChanges();
            });

            activity.setTapClickListener(header, v -> {
                if (total > 1) {
                    if (expanded.contains(group.packageName)) expanded.remove(group.packageName);
                    else expanded.add(group.packageName);
                    adapter.notifyDataSetChanged();
                } else {
                    toggle.setChecked(!toggle.isChecked());
                }
            });

            if (!isExpanded) {
                childrenContainer.setVisibility(View.GONE);
                return;
            }
            childrenContainer.setVisibility(View.VISIBLE);
            childrenList.removeAllViews();
            for (Entry entry : group.entries) {
                childrenList.addView(buildEntryRow(entry), activity.matchWrap());
            }
        }

        private String summaryText(AppGroup group, int shown, int total) {
            if (total == 1) {
                String entryLabel = group.entries.get(0).label;
                String state = shown == 0 ? "已隐藏" : "显示中";
                return entryLabel.equals(group.appName) ? state : entryLabel + " · " + state;
            }
            if (shown == total) return total + " 个入口 · 全部显示";
            if (shown == 0) return total + " 个入口 · 已全部隐藏";
            return shown + "/" + total + " 个入口显示中";
        }

        private View buildEntryRow(Entry entry) {
            boolean hidden = draft.isHidden(entry.component);
            LinearLayout row = row();
            row.setMinimumHeight(dp(44));
            row.setPadding(dp(4), dp(4), 0, dp(4));

            ImageView entryIcon = new ImageView(activity);
            entryIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            entryIcon.setImageDrawable(entry.icon);
            entryIcon.setAlpha(hidden ? 0.4f : 1f);
            entryIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(entryIcon, new LinearLayout.LayoutParams(dp(26), dp(26)));

            TextView label = text(entry.label, 14, activity.textColor());
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setAlpha(hidden ? 0.5f : 1f);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, -2, 1);
            labelLp.setMarginStart(dp(10));
            labelLp.setMarginEnd(dp(4));
            row.addView(label, labelLp);

            Switch entrySwitch = new Switch(activity);
            activity.styleSwitch(entrySwitch);
            entrySwitch.setChecked(!hidden);
            entrySwitch.setContentDescription(entry.label + "，显示开关");
            entrySwitch.setOnCheckedChangeListener((view, checked) -> {
                draft.setHidden(entry.component, !checked);
                applyChanges();
            });
            row.addView(entrySwitch);
            activity.setTapClickListener(row, v -> entrySwitch.setChecked(!entrySwitch.isChecked()));
            return row;
        }
    }

    private static final class AppGroup {
        final String packageName;
        final String appName;
        final Drawable appIcon;
        final List<Entry> entries = new ArrayList<>();

        AppGroup(String packageName, String appName, Drawable appIcon) {
            this.packageName = packageName;
            this.appName = appName;
            this.appIcon = appIcon;
        }

        List<String> components() {
            List<String> result = new ArrayList<>(entries.size());
            for (Entry entry : entries) result.add(entry.component);
            return result;
        }
    }

    private static final class Entry {
        final String component;
        final String label;
        final Drawable icon;

        Entry(String component, String label, Drawable icon) {
            this.component = component;
            this.label = label;
            this.icon = icon;
        }
    }

    private int dp(int value) { return activity.dp(value); }
    private LinearLayout column() {
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }
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
}
