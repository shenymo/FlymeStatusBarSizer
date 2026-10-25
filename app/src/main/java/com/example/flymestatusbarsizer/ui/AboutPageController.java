package com.example.flymestatusbarsizer.ui;

import com.example.flymestatusbarsizer.BuildConfig;
import com.example.flymestatusbarsizer.MainActivity;

import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

public final class AboutPageController {
    private AboutPageController() {
    }

    public static void bind(MainActivity activity, LinearLayout root) {
        root.addView(buildAppCard(activity, true), PageViewUtils.matchWrap());
        root.addView(buildProjectCard(activity, true), PageViewUtils.matchWrapWithTop(activity, 8));
        root.addView(buildBuildCard(activity, true), PageViewUtils.matchWrapWithTop(activity, 8));
    }

    private static View buildAppCard(MainActivity activity, boolean expanded) {
        PackageManager packageManager = activity.getPackageManager();
        ComponentName launcher = new ComponentName(activity, MainActivity.class.getName() + "Alias");
        Switch toggle = new Switch(activity);
        activity.styleSwitch(toggle);
        toggle.setText("隐藏桌面应用图标");
        activity.addSearchItem(toggle, toggle.getText().toString(), "隐藏后请从 LSPosed 模块页面打开设置；关闭此开关即可恢复桌面图标。");
        toggle.setTextColor(activity.textColor());
        toggle.setTextSize(16);
        toggle.setMinHeight(PageViewUtils.dp(activity, 44));
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
        return activity.buildSectionCard("应用入口",
                "隐藏后请从 LSPosed 模块页面打开设置；关闭此开关即可恢复桌面图标。", toggle, expanded);
    }

    private static View buildProjectCard(MainActivity activity, boolean expanded) {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(buildLinkRow(activity,
                "GitHub",
                "仓库地址",
                activity.githubUrl(),
                activity.githubUrl()),
                PageViewUtils.matchWrap());
        content.addView(buildDivider(activity), PageViewUtils.matchWrapWithTop(activity, 8));
        content.addView(buildLinkRow(activity,
                "QQ 群",
                "QQ群 " + activity.qqGroupNumber(),
                "加入群聊",
                activity.qqGroupUrl()),
                PageViewUtils.matchWrapWithTop(activity, 8));
        content.addView(buildDivider(activity), PageViewUtils.matchWrapWithTop(activity, 8));
        content.addView(buildLinkRow(activity,
                "QQ 频道",
                "QQ 群已满，可加入 QQ 频道“FlymeBarSizer”交流。",
                "加入频道",
                activity.qqChannelUrl()),
                PageViewUtils.matchWrapWithTop(activity, 8));
        return activity.buildSectionCard(
                "项目与交流",
                "提供项目仓库和交流入口。",
                content,
                expanded);
    }

    private static View buildBuildCard(MainActivity activity, boolean expanded) {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(buildInfoRow(activity, "当前版本", BuildConfig.VERSION_NAME), PageViewUtils.matchWrap());
        content.addView(buildDivider(activity), PageViewUtils.matchWrapWithTop(activity, 8));
        content.addView(buildInfoRow(activity, "构建日期", BuildConfig.BUILD_DATE), PageViewUtils.matchWrapWithTop(activity, 8));
        content.addView(buildDivider(activity), PageViewUtils.matchWrapWithTop(activity, 8));
        content.addView(buildInfoRow(activity, "支持范围", activity.supportedScopesSummary()), PageViewUtils.matchWrapWithTop(activity, 8));
        return activity.buildSectionCard(
                "版本与适用范围",
                "查看版本、构建日期和模块支持的应用范围。",
                content,
                expanded);
    }

    private static LinearLayout buildLinkRow(
            MainActivity activity,
            String titleText,
            String summaryText,
            String buttonText,
            String url) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(activity);
        title.setText(titleText);
        title.setTextColor(activity.textColor());
        title.setTextSize(16);
        row.addView(title, PageViewUtils.matchWrap());

        TextView summary = new TextView(activity);
        summary.setText(summaryText);
        summary.setTextColor(activity.subtextColor());
        summary.setTextSize(13);
        summary.setPadding(0, PageViewUtils.dp(activity, 4), 0, 0);
        row.addView(summary, PageViewUtils.matchWrap());

        TextView button = new TextView(activity);
        button.setText(buttonText);
        button.setTextColor(activity.primaryColor());
        button.setTextSize(13);
        button.setGravity(Gravity.CENTER);
        button.setPadding(
                PageViewUtils.dp(activity, 14),
                PageViewUtils.dp(activity, 6),
                PageViewUtils.dp(activity, 14),
                PageViewUtils.dp(activity, 6));
        button.setBackground(buildOutlinedButtonBackground(activity));
        button.setOnClickListener(v -> activity.openExternalLink(url));
        activity.addSearchItem(row, titleText, summaryText);
        row.addView(button, PageViewUtils.matchWrapWithTop(activity, 8));
        return row;
    }

    private static LinearLayout buildInfoRow(MainActivity activity, String titleText, String valueText) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(activity);
        title.setText(titleText);
        title.setTextColor(activity.textColor());
        title.setTextSize(16);
        row.addView(title, PageViewUtils.matchWrap());

        TextView value = new TextView(activity);
        value.setText(valueText);
        value.setTextColor(activity.subtextColor());
        value.setTextSize(13);
        value.setPadding(0, PageViewUtils.dp(activity, 4), 0, 0);
        row.addView(value, PageViewUtils.matchWrap());
        return row;
    }

    private static TextView buildDivider(MainActivity activity) {
        TextView divider = new TextView(activity);
        divider.setHeight(PageViewUtils.dp(activity, 1));
        divider.setBackgroundColor(activity.strokeColor());
        return divider;
    }

    private static GradientDrawable buildOutlinedButtonBackground(MainActivity activity) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(activity.surfaceColor());
        drawable.setCornerRadius(PageViewUtils.dp(activity, 999));
        drawable.setStroke(PageViewUtils.dp(activity, 1), activity.featureStrokeColor());
        return drawable;
    }
}
