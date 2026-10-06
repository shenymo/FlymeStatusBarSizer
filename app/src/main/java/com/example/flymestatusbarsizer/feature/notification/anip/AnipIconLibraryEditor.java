package com.example.flymestatusbarsizer.feature.notification.anip;

import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.example.flymestatusbarsizer.MainActivity;
import com.example.flymestatusbarsizer.R;
import com.example.flymestatusbarsizer.config.SettingsStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Searchable browser for the bundled ANIP icon library.
 *
 * <p>Shows what the bundle actually contains instead of replacing icons silently: how many
 * applications are covered, which artwork each one would use, and a per-application override so the
 * user can keep the desktop icon for the applications where ANIP's monochrome artwork is less
 * recognisable.
 */
public final class AnipIconLibraryEditor {
    private static final int MODE_FOLLOW_COLOR = 0xFF8A8A8E;
    private static final int MODE_ANIP_COLOR = 0xFF2AAE67;
    private static final int MODE_APPLICATION_COLOR = 0xFF6750A4;

    private final MainActivity activity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService loader = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "anip-icon-library");
        thread.setDaemon(true);
        return thread;
    });

    private Dialog dialog;
    private EditText search;
    private ListView list;
    private TextView summary;
    private TextView updateStatus;
    private TextView updateSourceHint;
    private android.widget.TextView updateButton;
    private boolean updateRunning;
    private View progress;
    private TextView emptyHint;

    /** Loaded rules, newest snapshot of the bundle; empty until {@link #load()} succeeds. */
    private List<AnipIconRule> rules = Collections.emptyList();
    private boolean loaded;
    private String query = "";
    private Map<String, Integer> overrides = Collections.emptyMap();
    private boolean anipEnabled;

    private final List<AnipIconRule> filtered = new ArrayList<>();
    private RuleAdapter adapter;
    private Dialog sheet;

    public AnipIconLibraryEditor(MainActivity activity) {
        this.activity = activity;
    }

    /** Opens the browser. */
    public void show() {
        anipEnabled = SettingsStore.readBoolean(
                activity.prefs(),
                SettingsStore.KEY_ANIP_ICON_ENABLED,
                SettingsStore.DEFAULT_ANIP_ICON_ENABLED);
        overrides = AnipIconMode.readOverrides(activity.prefs());

        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(false);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFitsSystemWindows(true);
        root.setPadding(activity.dp(16), activity.dp(4), activity.dp(16), 0);
        root.setBackgroundColor(activity.surfaceColor());

        root.addView(buildTitleRow(), activity.matchWrap());
        root.addView(buildSummary(), activity.matchWrapWithTop(4));
        root.addView(buildUpdateRow(), activity.matchWrapWithTop(10));
        root.addView(buildSearchRow(), activity.matchWrapWithTop(10));

        progress = statusText("正在读取图标库…", 24);
        root.addView(progress, activity.matchWrapWithTop(12));

        emptyHint = statusText("没有匹配的应用。", 16);
        emptyHint.setVisibility(View.GONE);
        root.addView(emptyHint, activity.matchWrapWithTop(12));

        list = new ListView(activity);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setCacheColorHint(Color.TRANSPARENT);
        adapter = new RuleAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < filtered.size()) {
                showModeSheet(filtered.get(position));
            }
        });
        root.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        dialog.setContentView(root);
        dialog.setOnDismissListener(ignored -> {
            closeSheet();
            loader.shutdownNow();
        });
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(activity.surfaceColor()));
            window.setLayout(-1, -1);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }
        load();
    }

    private View buildTitleRow() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        ImageView back = new ImageView(activity);
        back.setImageResource(R.drawable.ic_settings_back);
        back.setContentDescription("返回");
        back.setScaleType(ImageView.ScaleType.CENTER);
        activity.setTapClickListener(back, v -> dismiss());
        row.addView(back, new LinearLayout.LayoutParams(activity.dp(48), activity.dp(48)));

        LinearLayout heading = new LinearLayout(activity);
        heading.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(activity);
        title.setText("通知图标库");
        title.setTextColor(activity.textColor());
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        heading.addView(title);
        summary = new TextView(activity);
        summary.setTextColor(activity.subtextColor());
        summary.setTextSize(11);
        heading.addView(summary);
        row.addView(heading, new LinearLayout.LayoutParams(0, -2, 1f));
        return row;
    }

    private View buildSummary() {
        TextView hint = new TextView(activity);
        hint.setText("来自 ANIP（Android Notification Icon Project）的单色通知图标。"
                + "已收录的应用会用这里的图标替换桌面图标；未收录的应用保持原来的做法。"
                + "点击任意一行可以单独指定该应用用哪一种。");
        hint.setTextColor(activity.subtextColor());
        hint.setTextSize(11);
        hint.setLineSpacing(activity.dp(3), 1f);
        return hint;
    }

    /**
     * Update controls: shows where the catalog came from and lets the user pull a newer bundle.
     *
     * <p>The icons shipped inside the APK are already usable, so this is an optional refresh rather
     * than a required first-run download.
     */
    private View buildUpdateRow() {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(activity.dp(14), activity.dp(12), activity.dp(14), activity.dp(12));
        box.setBackground(activity.roundRect(activity.surfaceSoftColor(), 14));

        updateStatus = new TextView(activity);
        updateStatus.setTextColor(activity.textColor());
        updateStatus.setTextSize(13);
        box.addView(updateStatus);

        TextView source = new TextView(activity);
        source.setTextColor(activity.subtextColor());
        source.setTextSize(11);
        source.setPadding(0, activity.dp(2), 0, 0);
        box.addView(source);
        updateSourceHint = source;

        LinearLayout buttons = new LinearLayout(activity);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, activity.dp(10), 0, 0);
        updateButton = actionButton("检查更新", v -> checkForUpdate());
        buttons.addView(updateButton, actionParams());
        buttons.addView(actionButton("更新来源", v -> showSourceDialog()), actionParams());
        buttons.addView(actionButton("删除图标库", v -> removeDownloadedBundle()), actionParams());
        box.addView(buttons);
        refreshUpdateStatus();
        return box;
    }

    private android.widget.TextView actionButton(String text, View.OnClickListener listener) {
        android.widget.TextView button = new android.widget.TextView(activity);
        button.setText(text);
        button.setTextColor(activity.primaryColor());
        button.setTextSize(13);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(activity.dp(36));
        button.setBackground(activity.roundRect(activity.surfaceColor(), 12));
        activity.setTapClickListener(button, listener);
        return button;
    }

    private LinearLayout.LayoutParams actionParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.rightMargin = activity.dp(8);
        return lp;
    }

    private void refreshUpdateStatus() {
        if (updateStatus == null) {
            return;
        }
        updateStatus.setText("图标库：" + AnipIconUpdater.describeInstalled(activity));
        updateSourceHint.setText("更新来源：" + describeSource());
    }

    private String describeSource() {
        int type = AnipIconUpdater.sourceType(activity);
        if (type == AnipRemoteSource.SOURCE_STATIC) {
            String base = AnipIconUpdater.baseUrl(activity);
            return base == null || base.isEmpty() ? "自定义地址（未填写）" : "自定义地址 " + base;
        }
        if (type == AnipRemoteSource.SOURCE_GITHUB_DIRECT) {
            return "GitHub 直连";
        }
        return "GitHub 镜像";
    }

    private void checkForUpdate() {
        if (updateRunning) {
            return;
        }
        updateRunning = true;
        updateButton.setEnabled(false);
        updateStatus.setText("正在检查更新…");
        loader.execute(() -> {
            final AnipIconUpdater.CheckResult result = AnipIconUpdater.check(activity);
            if (!result.success) {
                main.post(() -> {
                    updateRunning = false;
                    updateButton.setEnabled(true);
                    updateStatus.setText(result.message);
                });
                return;
            }
            if (!result.updateAvailable) {
                main.post(() -> {
                    updateRunning = false;
                    updateButton.setEnabled(true);
                    refreshUpdateStatus();
                    activity.showToast(result.message);
                });
                return;
            }
            main.post(() -> updateStatus.setText("正在下载 " + result.release.tag + "…"));
            final boolean ok = AnipIconUpdater.download(activity, result.release);
            main.post(() -> {
                updateRunning = false;
                updateButton.setEnabled(true);
                if (ok) {
                    AnipIconUpdater.reloadLibrary(activity);
                    rules = new ArrayList<>(AnipIconLibrary.get().getRules());
                    query = "";
                    if (search != null) {
                        search.setText("");
                    }
                    refreshUpdateStatus();
                    applyQuery("");
                    activity.showToast("图标库已更新到 " + result.release.tag);
                } else {
                    updateStatus.setText("更新失败：下载或校验未通过，已保留原有图标");
                }
            });
        });
    }

    /** Removes the downloaded bundle; without it the notification hook keeps the desktop icon. */
    private void removeDownloadedBundle() {
        if (AnipBundleStore.resolve(activity) == null) {
            activity.showToast("尚未下载图标库");
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle("删除已下载的图标库")
                .setMessage("删除后通知图标会退回应用图标，直到再次下载。")
                .setPositiveButton("删除", (dialog, which) -> {
                    AnipBundleStore.clear(activity);
                    AnipIconUpdater.reloadLibrary(activity);
                    rules = new ArrayList<>(AnipIconLibrary.get().getRules());
                    refreshUpdateStatus();
                    applyQuery(query);
                    activity.showToast("已删除图标库");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showSourceDialog() {
        final int current = AnipIconUpdater.sourceType(activity);
        final String[] labels = {"GitHub 镜像（推荐，国内可用）", "GitHub 直连", "自定义地址"};
        final int[] values = {AnipRemoteSource.SOURCE_GITHUB_PROXY,
                AnipRemoteSource.SOURCE_GITHUB_DIRECT, AnipRemoteSource.SOURCE_STATIC};
        new AlertDialog.Builder(activity)
                .setTitle("更新来源")
                .setSingleChoiceItems(labels, indexOf(values, current), (dialog, which) -> {
                    activity.putIntSetting(SettingsStore.KEY_ANIP_SOURCE_TYPE, values[which]);
                    if (values[which] == AnipRemoteSource.SOURCE_STATIC) {
                        showBaseUrlDialog();
                    } else {
                        refreshUpdateStatus();
                    }
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showBaseUrlDialog() {
        final EditText input = new EditText(activity);
        input.setHint("https://你的地址/anip");
        input.setText(AnipIconUpdater.baseUrl(activity));
        input.setTextSize(13);
        int pad = activity.dp(16);
        input.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(activity)
                .setTitle("自定义地址")
                .setMessage("填写提供 anip-release.json 的目录地址，必须是 https。")
                .setView(input)
                .setPositiveButton("保存", (dialog, which) -> {
                    String value = input.getText() == null ? "" : input.getText().toString().trim();
                    activity.putStringSetting(SettingsStore.KEY_ANIP_BASE_URL, value);
                    refreshUpdateStatus();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private static int indexOf(int[] values, int target) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == target) {
                return i;
            }
        }
        return 0;
    }

    private View buildSearchRow() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(activity.roundRect(activity.surfaceSoftColor(), 18));
        row.setPadding(activity.dp(12), 0, activity.dp(8), 0);

        search = new EditText(activity);
        search.setHint("搜索应用名称或包名");
        search.setTextColor(activity.textColor());
        search.setHintTextColor(activity.subtextColor());
        search.setTextSize(14);
        search.setSingleLine(true);
        search.setBackground(null);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override public void afterTextChanged(Editable s) {
                applyQuery(s == null ? "" : s.toString());
            }
        });
        row.addView(search, new LinearLayout.LayoutParams(0, activity.dp(46), 1f));

        ImageView clear = new ImageView(activity);
        clear.setImageResource(R.drawable.ic_share_clear);
        clear.setContentDescription("清空搜索");
        clear.setScaleType(ImageView.ScaleType.CENTER);
        activity.setTapClickListener(clear, v -> search.setText(""));
        row.addView(clear, new LinearLayout.LayoutParams(activity.dp(36), activity.dp(36)));
        return row;
    }

    /** Centred secondary text block, used for both the loading state and the empty result. */
    private TextView statusText(String message, int verticalPaddingDp) {
        TextView text = new TextView(activity);
        text.setText(message);
        text.setTextColor(activity.subtextColor());
        text.setTextSize(13);
        text.setGravity(Gravity.CENTER);
        text.setPadding(0, activity.dp(verticalPaddingDp), 0, activity.dp(verticalPaddingDp));
        return text;
    }

    private void load() {
        loader.execute(() -> {
            final List<AnipIconRule> loadedRules;
            try {
                AnipIconLibrary library = AnipIconLibrary.get();
                AnipBundleStore.Installed installed = AnipBundleStore.resolve(activity);
                library.load(installed == null ? null : installed.directory);
                loadedRules = new ArrayList<>(library.getRules());
            } catch (Throwable ignored) {
                main.post(this::showLoadFailure);
                return;
            }
            main.post(() -> {
                if (dialog == null || !dialog.isShowing()) {
                    return;
                }
                rules = loadedRules;
                loaded = !rules.isEmpty();
                progress.setVisibility(View.GONE);
                refreshSummary();
                applyQuery(query);
            });
        });
    }

    private void showLoadFailure() {
        if (dialog == null || !dialog.isShowing()) {
            return;
        }
        progress.setVisibility(View.GONE);
        emptyHint.setVisibility(View.VISIBLE);
        summary.setText("图标库读取失败");
        emptyHint.setText("无法读取内置的通知图标库，请重新安装模块后重试。");
    }

    private void applyQuery(String value) {
        query = value == null ? "" : value.trim();
        filtered.clear();
        if (loaded) {
            String needle = query.toLowerCase(Locale.ROOT);
            for (AnipIconRule rule : rules) {
                if (needle.isEmpty()
                        || rule.getLabel().toLowerCase(Locale.ROOT).contains(needle)
                        || rule.getPackageName().toLowerCase(Locale.ROOT).contains(needle)) {
                    filtered.add(rule);
                }
            }
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        if (loaded) {
            emptyHint.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
            if (filtered.isEmpty()) {
                emptyHint.setText("没有匹配的应用。");
            }
        }
    }

    private void refreshSummary() {
        int available = rules.size();
        int anipCount = 0;
        int appCount = 0;
        for (AnipIconRule rule : rules) {
            int mode = effectiveMode(rule.getPackageName());
            if (mode == AnipIconMode.ANIP) {
                anipCount++;
            } else if (mode == AnipIconMode.APPLICATION) {
                appCount++;
            }
        }
        StringBuilder text = new StringBuilder();
        text.append("已收录 ").append(available).append(" 个应用");
        if (!overrides.isEmpty()) {
            text.append(" · 单独指定 ").append(overrides.size()).append(" 个");
        }
        if (anipCount > 0 || appCount > 0) {
            text.append(" · 强制 ANIP ").append(anipCount)
                    .append(" / 强制应用图标 ").append(appCount);
        }
        if (!anipEnabled) {
            text.append("\n总开关当前关闭，图标库不会生效。");
        }
        summary.setText(text.toString());
    }

    private int effectiveMode(String packageName) {
        Integer override = overrides.get(packageName);
        if (override != null) {
            return override.intValue();
        }
        return anipEnabled ? AnipIconMode.FOLLOW : AnipIconMode.APPLICATION;
    }

    private void showModeSheet(AnipIconRule rule) {
        closeSheet();
        sheet = new Dialog(activity);
        sheet.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(activity.dp(16), activity.dp(12), activity.dp(16), activity.dp(16));
        box.setBackgroundColor(activity.surfaceColor());

        TextView title = new TextView(activity);
        title.setText(rule.getLabel());
        title.setTextColor(activity.textColor());
        title.setTextSize(17);
        title.setTypeface(null, Typeface.BOLD);
        box.addView(title);

        TextView pkg = new TextView(activity);
        pkg.setText(rule.getPackageName());
        pkg.setTextColor(activity.subtextColor());
        pkg.setTextSize(11);
        box.addView(pkg);

        activity.addDivider(box);
        int current = effectiveMode(rule.getPackageName());
        addModeOption(box, rule, AnipIconMode.FOLLOW, "跟随总开关",
                "总开关打开时使用 ANIP 图标，关闭时用应用图标。", current);
        addModeOption(box, rule, AnipIconMode.ANIP, "始终用 ANIP 图标",
                "这个应用固定使用图标库里的单色图标。", current);
        addModeOption(box, rule, AnipIconMode.APPLICATION, "始终用应用图标",
                "这个应用固定使用桌面图标，忽略图标库。", current);

        sheet.setContentView(box);
        sheet.show();
        Window window = sheet.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(activity.surfaceColor()));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
        }
    }

    private void addModeOption(LinearLayout root, AnipIconRule rule, int mode,
            String titleText, String subtitleText, int current) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(activity.dp(14), activity.dp(12), activity.dp(14), activity.dp(12));
        row.setBackground(activity.roundRect(
                mode == current ? activity.primaryContainerColor() : activity.surfaceSoftColor(), 14));

        TextView title = new TextView(activity);
        title.setText(titleText + (mode == current ? "（当前）" : ""));
        title.setTextColor(modeColor(mode));
        title.setTextSize(15);
        row.addView(title);

        TextView subtitle = new TextView(activity);
        subtitle.setText(subtitleText);
        subtitle.setTextColor(activity.subtextColor());
        subtitle.setTextSize(11);
        row.addView(subtitle);

        activity.setTapClickListener(row, v -> {
            applyMode(rule.getPackageName(), mode);
            closeSheet();
        });
        LinearLayout.LayoutParams lp = activity.matchWrap();
        lp.topMargin = activity.dp(8);
        root.addView(row, lp);
    }

    private static int modeColor(int mode) {
        switch (mode) {
            case AnipIconMode.ANIP:
                return MODE_ANIP_COLOR;
            case AnipIconMode.APPLICATION:
                return MODE_APPLICATION_COLOR;
            default:
                return MODE_FOLLOW_COLOR;
        }
    }

    private void applyMode(String packageName, int mode) {
        overrides = new LinkedHashMap<>(
                AnipIconMode.withOverride(overrides, packageName, mode));
        // Persisting through the activity keeps the cross-process sync and the preview refresh
        // behaviour identical to every other settings write in this module.
        activity.putStringSetting(
                SettingsStore.KEY_ANIP_ICON_MODE, AnipIconMode.encodeOverrides(overrides));
        refreshSummary();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void closeSheet() {
        if (sheet != null) {
            sheet.dismiss();
            sheet = null;
        }
    }

    private void dismiss() {
        closeSheet();
        if (dialog != null) {
            dialog.dismiss();
        }
    }

    /** Row renderer: label, package name and the effective artwork source. */
    private final class RuleAdapter extends BaseAdapter {
        @Override public int getCount() {
            return filtered.size();
        }

        @Override public Object getItem(int position) {
            return filtered.get(position);
        }

        @Override public long getItemId(int position) {
            return position;
        }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            if (convertView instanceof LinearLayout) {
                row = (LinearLayout) convertView;
            } else {
                row = buildRow();
            }
            AnipIconRule rule = filtered.get(position);
            TextView label = (TextView) row.getChildAt(0);
            TextView pkg = (TextView) row.getChildAt(1);
            TextView status = (TextView) row.getChildAt(2);
            label.setText(rule.getLabel());
            pkg.setText(rule.getPackageName());
            int mode = effectiveMode(rule.getPackageName());
            status.setText(statusText(mode));
            status.setTextColor(modeColor(mode));
            return row;
        }

        private LinearLayout buildRow() {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(activity.dp(14), activity.dp(12), activity.dp(14), activity.dp(12));
            row.setBackground(activity.roundRect(activity.surfaceSoftColor(), 14));

            TextView label = new TextView(activity);
            label.setTextColor(activity.textColor());
            label.setTextSize(15);
            row.addView(label);

            TextView pkg = new TextView(activity);
            pkg.setTextColor(activity.subtextColor());
            pkg.setTextSize(11);
            row.addView(pkg);

            TextView status = new TextView(activity);
            status.setTextSize(11);
            status.setPadding(0, activity.dp(2), 0, 0);
            row.addView(status);
            return row;
        }

        private String statusText(int mode) {
            switch (mode) {
                case AnipIconMode.ANIP:
                    return "用 ANIP 图标";
                case AnipIconMode.APPLICATION:
                    return "用应用图标";
                default:
                    return anipEnabled ? "跟随总开关 · 用 ANIP 图标" : "跟随总开关 · 用应用图标";
            }
        }
    }
}
