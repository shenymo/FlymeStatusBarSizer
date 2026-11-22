package com.example.flymestatusbarsizer.ui;

import com.example.flymestatusbarsizer.MainActivity;
import com.example.flymestatusbarsizer.R;
import com.example.flymestatusbarsizer.config.SettingsStore;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.divider.MaterialDivider;
import com.google.android.material.materialswitch.MaterialSwitch;
import android.widget.TextView;

import java.util.Set;

public final class HomePageController {
    private HomePageController() {
    }

    public static void bind(MainActivity activity, LinearLayout root) {
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(activity.dp(4), activity.dp(8), activity.dp(4), activity.dp(4));

        LinearLayout titleCol = new LinearLayout(activity);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(activity, "FlymeBarSizer", 28, activity.textColor());
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titleCol.addView(title, PageViewUtils.matchWrap());

        TextView subtitle = text(activity, "状态栏与系统界面设置", 13, activity.subtextColor());
        titleCol.addView(subtitle, PageViewUtils.matchWrapWithTop(activity, 2));

        header.addView(titleCol, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        MaterialCardView donateCard = new MaterialCardView(activity);
        donateCard.setCardElevation(0);
        donateCard.setStrokeWidth(0);
        donateCard.setRadius(activity.dp(20));
        donateCard.setCardBackgroundColor(activity.surfaceStrongColor());
        ImageView donate = new ImageView(activity);
        donate.setImageResource(R.drawable.ic_donate_copy);
        donate.setColorFilter(activity.primaryColor());
        donate.setContentDescription("捐赠支持");
        donate.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        donate.setPadding(activity.dp(10), activity.dp(10), activity.dp(10), activity.dp(10));
        donateCard.addView(donate, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        activity.setTapClickListener(donateCard, v -> activity.openPage(MainActivity.Page.DONATION));
        header.addView(donateCard, new LinearLayout.LayoutParams(activity.dp(40), activity.dp(40)));

        root.addView(header, PageViewUtils.matchWrap());

        LinearLayout content = activity.addFeatureSearch(root);
        content.addView(buildModuleToggleCard(activity),
                PageViewUtils.matchWrapWithTop(activity, 12));
        content.addView(buildRestartCard(activity),
                PageViewUtils.matchWrapWithTop(activity, 10));

        LinearLayout personal = addGroup(activity, content, "功能设置");
        addEntry(activity, personal, "状态栏图标与电池", "电池、状态栏图标、通知图标与移动网络",
                R.drawable.ic_settings_battery, MainActivity.Page.ICONS_BATTERY);
        addEntry(activity, personal, "时间与网速", "实时网速、时间格式、时钟交互与锁屏大时钟",
                R.drawable.ic_settings_clock, MainActivity.Page.TIME_NETWORK);
        addEntry(activity, personal, "系统外观", "摄像头挖孔、图标颜色、桌面整理与系统界面外观",
                R.drawable.ic_settings_appearance, MainActivity.Page.SYSTEM_APPEARANCE);
        addEntry(activity, personal, "手势与系统交互", "侧边手势、单手模式、MBack、小窗与最近任务",
                R.drawable.ic_settings_interaction, MainActivity.Page.SYSTEM_INTERACTION);

        LinearLayout tools = addGroup(activity, content, "工具与支持");
        addEntry(activity, tools, "工具与调试", "配置备份与恢复、性能日志与调试工具",
                R.drawable.ic_settings_tune, MainActivity.Page.ADVANCED_DEBUG);
        addEntry(activity, tools, "关于与支持", "版本信息、开源地址、适用范围与交流",
                R.drawable.ic_settings_info, MainActivity.Page.ABOUT);
    }

    private static View buildRestartCard(MainActivity activity) {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        activity.bindRestartCardContent(content);
        View card = activity.buildSectionCard("快速重启", "配置更新后重启对应应用使修改生效", content);
        if (card instanceof MaterialCardView) {
            ((MaterialCardView) card).setCardBackgroundColor(activity.surfaceSoftColor());
            ((MaterialCardView) card).setStrokeWidth(0);
            ((MaterialCardView) card).setRadius(activity.dp(24));
        }
        return card;
    }

    private static View buildModuleToggleCard(MainActivity activity) {
        MaterialCardView card = new MaterialCardView(activity);
        card.setCardElevation(0);
        card.setStrokeWidth(0);
        card.setRadius(activity.dp(24));
        card.setContentPadding(activity.dp(16), activity.dp(16), activity.dp(16), activity.dp(16));
        card.setMinimumHeight(activity.dp(80));

        boolean enabled = SettingsStore.readBoolean(activity.prefs(),
                SettingsStore.KEY_ENABLED, SettingsStore.DEFAULT_ENABLED);

        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);

        ImageView icon = new ImageView(activity);
        icon.setImageResource(R.drawable.ic_module_power);
        icon.setPadding(activity.dp(11), activity.dp(11), activity.dp(11), activity.dp(11));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(icon, new LinearLayout.LayoutParams(activity.dp(46), activity.dp(46)));

        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        copyLp.leftMargin = activity.dp(16);
        copyLp.rightMargin = activity.dp(8);

        TextView title = text(activity, enabled ? "模块已启用" : "模块未启用", 17,
                enabled ? activity.primaryDeepColor() : activity.textColor());
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        copy.addView(title, PageViewUtils.matchWrap());

        TextView summary = text(activity, enabled ? "界面增强与配置已生效" : "未生效 · 点击开启以应用所有设置",
                12, activity.subtextColor());
        copy.addView(summary, PageViewUtils.matchWrapWithTop(activity, 2));

        row.addView(copy, copyLp);

        MaterialSwitch toggle = new MaterialSwitch(activity);
        activity.styleSwitch(toggle);
        toggle.setChecked(enabled);
        toggle.setContentDescription("启用模块，总开关");
        row.addView(toggle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        card.addView(row, PageViewUtils.matchWrap());

        applyModuleToggleVisuals(activity, card, icon, title, summary, enabled, false);

        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                activity.performTapHaptic(buttonView);
            }
            activity.putBooleanSetting(SettingsStore.KEY_ENABLED, isChecked);
            applyModuleToggleVisuals(activity, card, icon, title, summary, isChecked, true);
        });
        activity.setTapClickListener(card, v -> toggle.setChecked(!toggle.isChecked()));
        return card;
    }

    private static void applyModuleToggleVisuals(MainActivity activity, MaterialCardView card,
            ImageView icon, TextView title, TextView summary, boolean enabled, boolean animate) {
        int targetBg = enabled ? activity.primaryContainerColor() : activity.surfaceSoftColor();
        card.setCardBackgroundColor(targetBg);
        card.setStrokeWidth(0);

        if (enabled) {
            icon.setBackground(activity.roundRect(activity.primaryColor(), 16));
            icon.setColorFilter(Color.WHITE);
            title.setText("模块已启用");
            title.setTextColor(activity.primaryDeepColor());
            summary.setText("界面增强与配置已生效");
            summary.setTextColor(activity.primaryDeepColor());
            summary.setAlpha(0.75f);
        } else {
            icon.setBackground(activity.roundRect(activity.surfaceStrongColor(), 16));
            icon.setColorFilter(activity.subtextColor());
            title.setText("模块未启用");
            title.setTextColor(activity.textColor());
            summary.setText("未生效 · 点击开启以应用所有设置");
            summary.setTextColor(activity.subtextColor());
            summary.setAlpha(1.0f);
        }

        if (animate) {
            int prevBg = enabled ? activity.surfaceSoftColor() : activity.primaryContainerColor();
            ValueAnimator animator = ValueAnimator.ofObject(new ArgbEvaluator(), prevBg, targetBg);
            animator.setDuration(220L);
            animator.addUpdateListener(value -> card.setCardBackgroundColor((Integer) value.getAnimatedValue()));
            animator.start();
        }
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
        TextView heading = text(activity, title, 13, activity.primaryColor());
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heading.setPadding(activity.dp(16), 0, 0, 0);
        root.addView(heading, PageViewUtils.matchWrapWithTop(activity, 20));

        MaterialCardView card = new MaterialCardView(activity);
        card.setCardElevation(0);
        card.setStrokeWidth(0);
        card.setRadius(activity.dp(24));
        card.setCardBackgroundColor(activity.surfaceSoftColor());
        card.setContentPadding(0, 0, 0, 0);

        LinearLayout group = new LinearLayout(activity);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setPadding(0, 0, 0, 0);
        card.addView(group, PageViewUtils.matchWrap());
        root.addView(card, PageViewUtils.matchWrapWithTop(activity, 8));
        return group;
    }

    private static void addEntry(MainActivity activity, LinearLayout group, String title,
            String summary, int iconRes, MainActivity.Page page) {
        if (group.getChildCount() > 0) {
            MaterialDivider divider = new MaterialDivider(activity);
            divider.setDividerColor(activity.surfaceStrongColor());
            divider.setDividerInsetStart(activity.dp(74));
            divider.setDividerInsetEnd(activity.dp(16));
            divider.setDividerThickness(activity.dp(1));
            group.addView(divider, PageViewUtils.matchWrap());
        }
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(activity.dp(68));
        row.setPadding(activity.dp(16), activity.dp(12), activity.dp(16), activity.dp(12));
        activity.setTapClickListener(row, v -> activity.openPage(page));

        ImageView icon = new ImageView(activity);
        icon.setImageResource(iconRes);
        icon.setColorFilter(activity.primaryColor());
        icon.setPadding(activity.dp(10), activity.dp(10), activity.dp(10), activity.dp(10));
        icon.setBackground(activity.roundRect(activity.secondarySoftColor(), 14));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(icon, new LinearLayout.LayoutParams(activity.dp(44), activity.dp(44)));

        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(activity, title, 16, activity.textColor());
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        texts.addView(name, PageViewUtils.matchWrap());
        if (summary != null && !summary.isEmpty()) {
            TextView sumView = text(activity, summary, 12, activity.subtextColor());
            texts.addView(sumView, PageViewUtils.matchWrapWithTop(activity, 3));
        }
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textLp.leftMargin = activity.dp(14);
        textLp.rightMargin = activity.dp(8);
        row.addView(texts, textLp);

        ImageView chevron = new ImageView(activity);
        chevron.setImageResource(R.drawable.ic_settings_chevron);
        chevron.setColorFilter(activity.subtextColor());
        chevron.setAlpha(0.6f);
        chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(chevron, new LinearLayout.LayoutParams(activity.dp(20), activity.dp(20)));
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
