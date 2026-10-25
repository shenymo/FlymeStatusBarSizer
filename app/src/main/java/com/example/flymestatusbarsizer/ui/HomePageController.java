package com.example.flymestatusbarsizer.ui;

import com.example.flymestatusbarsizer.MainActivity;
import com.example.flymestatusbarsizer.R;

import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Set;

public final class HomePageController {
    private HomePageController() {
    }

    public static void bind(MainActivity activity, LinearLayout root) {
        TextView title = text(activity, "FlymeBarSizer", 24, activity.textColor());
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout titleRow = new LinearLayout(activity);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        ImageView donate = new ImageView(activity);
        donate.setImageResource(R.drawable.ic_donate_copy);
        donate.setColorFilter(activity.primaryColor());
        donate.setContentDescription("捐赠");
        donate.setPadding(activity.dp(8), activity.dp(8), activity.dp(8), activity.dp(8));
        donate.setBackground(activity.roundRect(activity.surfaceColor(), 8));
        activity.setTapClickListener(donate, v -> activity.openPage(MainActivity.Page.DONATION));
        LinearLayout.LayoutParams donateLp = new LinearLayout.LayoutParams(activity.dp(40), activity.dp(40));
        donateLp.leftMargin = activity.dp(12);
        titleRow.addView(donate, donateLp);
        root.addView(titleRow, PageViewUtils.matchWrapWithTop(activity, 4));
        LinearLayout content = activity.addFeatureSearch(root);
        content.addView(buildRestartCard(activity), PageViewUtils.matchWrapWithTop(activity, 12));

        LinearLayout personal = addGroup(activity, content, "功能设置");
        addEntry(activity, personal, "状态栏图标与电池", null,
                R.drawable.ic_settings_battery, MainActivity.Page.ICONS_BATTERY);
        addEntry(activity, personal, "时间与网速", null,
                R.drawable.ic_settings_clock, MainActivity.Page.TIME_NETWORK);
        addEntry(activity, personal, "系统外观", null,
                R.drawable.ic_settings_appearance, MainActivity.Page.SYSTEM_APPEARANCE);
        addEntry(activity, personal, "手势与系统交互", null,
                R.drawable.ic_settings_interaction, MainActivity.Page.SYSTEM_INTERACTION);

        LinearLayout tools = addGroup(activity, content, "工具");
        addEntry(activity, tools, "工具与调试", null,
                R.drawable.ic_settings_tune, MainActivity.Page.ADVANCED_DEBUG);
        addEntry(activity, tools, "关于与支持", null,
                R.drawable.ic_settings_info, MainActivity.Page.ABOUT);
    }

    private static View buildRestartCard(MainActivity activity) {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        activity.bindRestartCardContent(content);
        View card = activity.buildSectionCard("重启相关组件", null, content);
        card.setBackground(activity.roundRect(activity.primaryContainerColor(), 8));
        return card;
    }

    public static void renderRestartTargets(MainActivity activity, LinearLayout content,
            Set<String> enabledScope, boolean loading) {
        content.removeAllViews();
        if (loading) {
            content.addView(text(activity, "正在读取已启用范围…", 14, activity.subtextColor()));
            return;
        }
        if (enabledScope == null) {
            content.addView(text(activity, "无法读取已启用范围，请确认 LSPosed 和模块已启用。",
                    14, activity.subtextColor()));
            activity.addActionButtonRow(content, "重新读取已启用范围",
                    "连接框架后重试；也可从 LSPosed 返回应用以自动刷新。",
                    "重试", activity::refreshRestartScope);
            return;
        }
        int batchCount = activity.getBatchRestartTargets(enabledScope).size();
        boolean restarting = activity.isBatchRestartRunning();
        if (batchCount > 0) {
            TextView button = activity.addActionButtonRow(content, "重启全部相关应用",
                    "按顺序重启当前列表中的相关应用，最后重启系统桌面和 SystemUI。完成后显示结果。",
                    restarting ? "重启中…" : "重启全部", () -> activity.restartAllScopeApps(enabledScope));
            button.setEnabled(!restarting);
            button.setAlpha(restarting ? 0.4f : 1f);
            content.addView(text(activity, "共 " + batchCount + " 个应用，不包含系统框架，不会重启手机。",
                    12, activity.subtextColor()));
        }
        for (RestartTarget target : RestartTarget.values()) {
            if (!enabledScope.contains(target.packageName) || !activity.isRestartTargetInstalled(target)) {
                continue;
            }
            if (content.getChildCount() > 0) {
                activity.addDivider(content);
            }
            String buttonText = target == RestartTarget.FRAMEWORK ? "重启手机" : "重启";
            TextView button = activity.addActionButtonRow(content, target.label, target.summary,
                    buttonText, () -> activity.restartScopeApp(target));
            button.setContentDescription(target.label + "，" + buttonText);
            button.setEnabled(!restarting);
            button.setAlpha(restarting ? 0.4f : 1f);
        }
        if (content.getChildCount() == 0) {
            String message = enabledScope.isEmpty()
                    ? "尚未启用任何范围，请在 LSPosed 中勾选目标应用后返回。"
                    : "已启用范围中没有可重启的已安装应用。";
            content.addView(text(activity, message, 14, activity.subtextColor()));
        }
    }

    private static LinearLayout addGroup(MainActivity activity, LinearLayout root, String title) {
        TextView heading = text(activity, title, 13, activity.subtextColor());
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heading.setPadding(activity.dp(4), 0, 0, 0);
        root.addView(heading, PageViewUtils.matchWrapWithTop(activity, 16));
        LinearLayout group = activity.card(activity.surfaceColor(), 8);
        group.setPadding(0, 0, 0, 0);
        group.setClipToOutline(true);
        root.addView(group, PageViewUtils.matchWrapWithTop(activity, 6));
        return group;
    }

    private static void addEntry(MainActivity activity, LinearLayout group, String title,
            String summary, int iconRes, MainActivity.Page page) {
        if (group.getChildCount() > 0) {
            View divider = new View(activity);
            divider.setBackgroundColor(activity.surfaceSoftColor());
            LinearLayout.LayoutParams line = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, activity.dp(1));
            line.leftMargin = activity.dp(72);
            line.rightMargin = activity.dp(16);
            group.addView(divider, line);
        }
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(activity.dp(56));
        row.setPadding(activity.dp(16), activity.dp(8), activity.dp(16), activity.dp(8));
        activity.setTapClickListener(row, v -> activity.openPage(page));

        ImageView icon = new ImageView(activity);
        icon.setImageResource(iconRes);
        icon.setColorFilter(activity.primaryColor());
        icon.setPadding(activity.dp(10), activity.dp(10), activity.dp(10), activity.dp(10));
        icon.setBackground(activity.roundRect(activity.surfaceSoftColor(), 8));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(icon, new LinearLayout.LayoutParams(activity.dp(40), activity.dp(40)));

        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(activity, title, 16, activity.textColor());
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        texts.addView(name, PageViewUtils.matchWrap());
        if (summary != null && !summary.isEmpty()) {
            texts.addView(text(activity, summary, 12, activity.subtextColor()),
                    PageViewUtils.matchWrapWithTop(activity, 4));
        }
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textLp.leftMargin = activity.dp(16);
        textLp.rightMargin = activity.dp(8);
        row.addView(texts, textLp);

        TextView arrow = text(activity, "›", 22, activity.subtextColor());
        arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(arrow, PageViewUtils.wrapWrap());
        group.addView(row, PageViewUtils.matchWrap());
    }

    private static TextView text(MainActivity activity, String value, int size, int color) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }
}
