package com.example.flymestatusbarsizer.ui;

import com.example.flymestatusbarsizer.BuildConfig;
import com.example.flymestatusbarsizer.MainActivity;

import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

public final class AboutPageController {
    private AboutPageController() {
    }

    public static void bind(MainActivity activity, LinearLayout root) {
        addGroupTitle(activity, root, "应用入口");
        LinearLayout appRow = new LinearLayout(activity);
        appRow.setGravity(Gravity.CENTER_VERTICAL);
        appRow.setMinimumHeight(PageViewUtils.dp(activity, 56));
        TextView appTitle = buildText(activity, "隐藏桌面应用图标", 16, activity.textColor());
        appRow.addView(appTitle, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        PackageManager packageManager = activity.getPackageManager();
        ComponentName launcher = new ComponentName(activity, MainActivity.class.getName() + "Alias");
        Switch toggle = new Switch(activity);
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
        appRow.addView(toggle, PageViewUtils.wrapWrap());
        activity.addSearchItem(appRow, "隐藏桌面应用图标", null);
        root.addView(appRow, PageViewUtils.matchWrap());

        addGroupTitle(activity, root, "项目与交流");
        addClickableRow(activity, root, "GitHub", null, "›",
                () -> activity.openExternalLink(activity.githubUrl()));
        addDivider(activity, root);
        addClickableRow(activity, root, "QQ群", activity.qqGroupNumber(), "›",
                () -> activity.openExternalLink(activity.qqGroupUrl()));
        addDivider(activity, root);
        addClickableRow(activity, root, "QQ频道", "FlymeBarSizer", "›",
                () -> activity.openExternalLink(activity.qqChannelUrl()));

        addGroupTitle(activity, root, "版本");
        addValueRow(activity, root, "当前版本", BuildConfig.VERSION_NAME);
        addDivider(activity, root);
        addClickableRow(activity, root, "检查更新", null, "›", activity::checkForUpdatesManually);
        addDivider(activity, root);
        addClickableRow(activity, root, "版本与适用范围", null, "›",
                () -> activity.openPage(MainActivity.Page.ABOUT_DETAILS));
    }

    public static void bindDetails(MainActivity activity, LinearLayout root) {
        addGroupTitle(activity, root, "版本");
        addValueRow(activity, root, "当前版本", BuildConfig.VERSION_NAME);
        addDivider(activity, root);
        addValueRow(activity, root, "构建日期", BuildConfig.BUILD_DATE);

        addGroupTitle(activity, root, "适用范围");
        TextView scope = buildText(activity, activity.supportedScopesSummary(), 14,
                activity.subtextColor());
        scope.setPadding(0, PageViewUtils.dp(activity, 8), 0, PageViewUtils.dp(activity, 12));
        activity.addSearchItem(scope, "适用范围", activity.supportedScopesSummary());
        root.addView(scope, PageViewUtils.matchWrap());
    }

    private static void addGroupTitle(MainActivity activity, LinearLayout root, String label) {
        TextView title = buildText(activity, label, 13, activity.subtextColor());
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setPadding(PageViewUtils.dp(activity, 4), 0, 0, 0);
        root.addView(title, PageViewUtils.matchWrapWithTop(activity, 16));
    }

    private static void addClickableRow(MainActivity activity, LinearLayout root, String label,
            String value, String trailing, Runnable action) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(PageViewUtils.dp(activity, 52));
        row.setPadding(PageViewUtils.dp(activity, 4), PageViewUtils.dp(activity, 6),
                PageViewUtils.dp(activity, 4), PageViewUtils.dp(activity, 6));
        TextView title = buildText(activity, label, 16, activity.textColor());
        row.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (value != null) {
            TextView valueView = buildText(activity, value, 14, activity.subtextColor());
            valueView.setPadding(0, 0, PageViewUtils.dp(activity, 8), 0);
            row.addView(valueView, PageViewUtils.wrapWrap());
        }
        TextView arrow = buildText(activity, trailing, 22, activity.subtextColor());
        arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(arrow, PageViewUtils.wrapWrap());
        activity.setTapClickListener(row, v -> action.run());
        activity.addSearchItem(row, label, value);
        root.addView(row, PageViewUtils.matchWrap());
    }

    private static void addValueRow(MainActivity activity, LinearLayout root,
            String label, String value) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(PageViewUtils.dp(activity, 48));
        TextView title = buildText(activity, label, 16, activity.textColor());
        row.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(buildText(activity, value, 14, activity.subtextColor()),
                PageViewUtils.wrapWrap());
        activity.addSearchItem(row, label, value);
        root.addView(row, PageViewUtils.matchWrap());
    }

    private static void addDivider(MainActivity activity, LinearLayout root) {
        View divider = new View(activity);
        divider.setBackgroundColor(activity.strokeColor());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, PageViewUtils.dp(activity, 1));
        params.leftMargin = PageViewUtils.dp(activity, 4);
        root.addView(divider, params);
    }

    private static TextView buildText(MainActivity activity, String value, int size, int color) {
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        return text;
    }
}
