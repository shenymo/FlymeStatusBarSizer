package com.example.flymestatusbarsizer.ui;

import com.example.flymestatusbarsizer.BuildConfig;
import com.example.flymestatusbarsizer.MainActivity;
import com.example.flymestatusbarsizer.R;

import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.divider.MaterialDivider;
import com.google.android.material.materialswitch.MaterialSwitch;
import android.widget.TextView;

public final class AboutPageController {
    private AboutPageController() {
    }

    public static void bind(MainActivity activity, LinearLayout root) {
        LinearLayout appGroup = addGroup(activity, root, "应用入口");
        LinearLayout appRow = new LinearLayout(activity);
        appRow.setGravity(Gravity.CENTER_VERTICAL);
        appRow.setMinimumHeight(PageViewUtils.dp(activity, 56));
        appRow.setPadding(activity.dp(16), activity.dp(12), activity.dp(16), activity.dp(12));
        TextView appTitle = buildText(activity, "隐藏桌面应用图标", 16, activity.textColor());
        appRow.addView(appTitle, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        PackageManager packageManager = activity.getPackageManager();
        ComponentName launcher = new ComponentName(activity, MainActivity.class.getName() + "Alias");
        MaterialSwitch toggle = new MaterialSwitch(activity);
        activity.styleSwitch(toggle);
        toggle.setChecked(packageManager.getComponentEnabledSetting(launcher)
                == PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        toggle.setOnCheckedChangeListener((button, hidden) -> {
            if (hidden == (packageManager.getComponentEnabledSetting(launcher)
                    == PackageManager.COMPONENT_ENABLED_STATE_DISABLED)) {
                return;
            }
            activity.performTapHaptic(button);
            try {
                packageManager.setComponentEnabledSetting(launcher,
                        hidden ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                                : PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                        PackageManager.DONT_KILL_APP);
            } catch (IllegalArgumentException | SecurityException e) {
                button.setChecked(packageManager.getComponentEnabledSetting(launcher)
                        == PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
                activity.showToast("桌面图标设置失败");
            }
        });
        activity.setTapClickListener(appRow, v -> toggle.setChecked(!toggle.isChecked()));
        appRow.addView(toggle, PageViewUtils.wrapWrap());
        activity.addSearchItem(appRow, "隐藏桌面应用图标", null);
        appGroup.addView(appRow, PageViewUtils.matchWrap());

        LinearLayout linksGroup = addGroup(activity, root, "项目与交流");
        addClickableRow(activity, linksGroup, "GitHub", "开源项目主页与源码",
                () -> activity.openExternalLink(activity.githubUrl()));
        addDivider(activity, linksGroup);
        addClickableRow(activity, linksGroup, "QQ群", activity.qqGroupNumber(),
                () -> activity.openExternalLink(activity.qqGroupUrl()));
        addDivider(activity, linksGroup);
        addClickableRow(activity, linksGroup, "QQ频道", "FlymeBarSizer",
                () -> activity.openExternalLink(activity.qqChannelUrl()));

        LinearLayout versionGroup = addGroup(activity, root, "版本");
        addValueRow(activity, versionGroup, "当前版本", BuildConfig.VERSION_NAME);
        addDivider(activity, versionGroup);
        addClickableRow(activity, versionGroup, "检查更新", "获取最新版本更新", activity::checkForUpdatesManually);
        addDivider(activity, versionGroup);
        addClickableRow(activity, versionGroup, "版本与适用范围", "查看适配系统及支持功能",
                () -> activity.openPage(MainActivity.Page.ABOUT_DETAILS));
    }

    public static void bindDetails(MainActivity activity, LinearLayout root) {
        LinearLayout versionGroup = addGroup(activity, root, "版本信息");
        addValueRow(activity, versionGroup, "当前版本", BuildConfig.VERSION_NAME);
        addDivider(activity, versionGroup);
        addValueRow(activity, versionGroup, "构建日期", BuildConfig.BUILD_DATE);

        LinearLayout scopeGroup = addGroup(activity, root, "适用范围");
        TextView scope = buildText(activity, activity.supportedScopesSummary(), 14,
                activity.subtextColor());
        scope.setPadding(activity.dp(16), activity.dp(14), activity.dp(16), activity.dp(16));
        activity.addSearchItem(scope, "适用范围", activity.supportedScopesSummary());
        scopeGroup.addView(scope, PageViewUtils.matchWrap());
    }

    private static LinearLayout addGroup(MainActivity activity, LinearLayout root, String title) {
        TextView heading = buildText(activity, title, 13, activity.primaryColor());
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heading.setPadding(activity.dp(16), 0, 0, 0);
        root.addView(heading, PageViewUtils.matchWrapWithTop(activity, 18));

        MaterialCardView card = new MaterialCardView(activity);
        card.setCardElevation(0);
        card.setStrokeWidth(0);
        card.setRadius(activity.dp(20));
        card.setCardBackgroundColor(activity.surfaceSoftColor());
        card.setContentPadding(0, 0, 0, 0);

        LinearLayout group = new LinearLayout(activity);
        group.setOrientation(LinearLayout.VERTICAL);
        card.addView(group, PageViewUtils.matchWrap());
        root.addView(card, PageViewUtils.matchWrapWithTop(activity, 6));
        return group;
    }

    private static void addClickableRow(MainActivity activity, LinearLayout group, String label,
            String value, Runnable action) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(PageViewUtils.dp(activity, 56));
        row.setPadding(activity.dp(16), activity.dp(12), activity.dp(16), activity.dp(12));

        LinearLayout textCol = new LinearLayout(activity);
        textCol.setOrientation(LinearLayout.VERTICAL);
        TextView title = buildText(activity, label, 16, activity.textColor());
        textCol.addView(title, PageViewUtils.matchWrap());

        if (value != null && !value.isEmpty()) {
            TextView valueView = buildText(activity, value, 12, activity.subtextColor());
            textCol.addView(valueView, PageViewUtils.matchWrapWithTop(activity, 2));
        }
        row.addView(textCol, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        ImageView chevron = new ImageView(activity);
        chevron.setImageResource(R.drawable.ic_settings_chevron);
        chevron.setColorFilter(activity.subtextColor());
        chevron.setAlpha(0.6f);
        chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(chevron, new LinearLayout.LayoutParams(activity.dp(18), activity.dp(18)));

        activity.setTapClickListener(row, v -> action.run());
        activity.addSearchItem(row, label, value);
        group.addView(row, PageViewUtils.matchWrap());
    }

    private static void addValueRow(MainActivity activity, LinearLayout group,
            String label, String value) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(PageViewUtils.dp(activity, 52));
        row.setPadding(activity.dp(16), activity.dp(12), activity.dp(16), activity.dp(12));
        TextView title = buildText(activity, label, 16, activity.textColor());
        row.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView valView = buildText(activity, value, 14, activity.subtextColor());
        row.addView(valView, PageViewUtils.wrapWrap());
        activity.addSearchItem(row, label, value);
        group.addView(row, PageViewUtils.matchWrap());
    }

    private static void addDivider(MainActivity activity, LinearLayout group) {
        MaterialDivider divider = new MaterialDivider(activity);
        divider.setDividerColor(activity.surfaceStrongColor());
        divider.setDividerInsetStart(activity.dp(16));
        divider.setDividerInsetEnd(activity.dp(16));
        divider.setDividerThickness(activity.dp(1));
        group.addView(divider, PageViewUtils.matchWrap());
    }

    private static TextView buildText(MainActivity activity, String value, int size, int color) {
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        return text;
    }
}
