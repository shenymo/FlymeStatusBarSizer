package com.example.flymestatusbarsizer;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

final class HomePageController {
    private HomePageController() {
    }

    static void bind(MainActivity activity, LinearLayout root) {
        root.addView(buildHeroCard(activity), PageViewUtils.matchWrap());
        root.addView(buildSectionLabel(activity, "功能入口"), PageViewUtils.matchWrapWithTop(activity, 18));
        root.addView(buildEntryCard(activity,
                "IB",
                "图标与电池",
                "图标缩放、电池样式、通知图标和信号接管",
                activity.primaryColor(),
                0x1A005CAE,
                v -> activity.openPage(MainActivity.Page.ICONS_BATTERY)),
                PageViewUtils.matchWrapWithTop(activity, 12));
        root.addView(buildEntryCard(activity,
                "TN",
                "时间与网络",
                "实时网速阈值、时间表达式和时间字体",
                activity.secondaryColor(),
                0x1A006688,
                v -> activity.openPage(MainActivity.Page.TIME_NETWORK)),
                PageViewUtils.matchWrapWithTop(activity, 12));
        root.addView(buildEntryCard(activity,
                "SI",
                "系统交互",
                "MBack 长触、导航栏沉浸和输入法控制栏",
                activity.tertiaryColor(),
                0x1A964500,
                v -> activity.openPage(MainActivity.Page.SYSTEM_INTERACTION)),
                PageViewUtils.matchWrapWithTop(activity, 12));
        root.addView(buildEntryCard(activity,
                "AD",
                "高级与调试",
                "配置管理、SystemUI 操作、布局微调和 Telephony 调试",
                activity.errorColor(),
                0x1ABA1A1A,
                v -> activity.openPage(MainActivity.Page.ADVANCED_DEBUG)),
                PageViewUtils.matchWrapWithTop(activity, 12));
        root.addView(buildFooterCard(activity), PageViewUtils.matchWrapWithTop(activity, 20));
    }

    private static View buildHeroCard(MainActivity activity) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(
                PageViewUtils.dp(activity, 20),
                PageViewUtils.dp(activity, 20),
                PageViewUtils.dp(activity, 20),
                PageViewUtils.dp(activity, 20));
        card.setBackground(buildHeroBackground(activity));

        TextView chip = buildChip(activity, "Design Refresh", activity.primaryColor(), Color.WHITE);
        card.addView(chip, PageViewUtils.matchWrap());

        TextView title = new TextView(activity);
        title.setText("FlymeStatusBarSizer");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setPadding(0, PageViewUtils.dp(activity, 14), 0, 0);
        card.addView(title, PageViewUtils.matchWrap());

        TextView summary = new TextView(activity);
        summary.setText("首页只负责导航、状态信息和轻说明；原有设置键与写回逻辑继续复用。");
        summary.setTextColor(0xE6FFFFFF);
        summary.setTextSize(14);
        summary.setPadding(0, PageViewUtils.dp(activity, 8), 0, 0);
        card.addView(summary, PageViewUtils.matchWrap());

        TextView detail = new TextView(activity);
        detail.setText("不提供模块总开关，不改 Hook 结构，只把配置入口收拢成更稳定的二级页。");
        detail.setTextColor(0xCCFFFFFF);
        detail.setTextSize(13);
        detail.setPadding(0, PageViewUtils.dp(activity, 12), 0, 0);
        card.addView(detail, PageViewUtils.matchWrap());

        TextView aboutButton = buildChip(activity, "关于与支持", Color.WHITE, activity.primaryDeepColor());
        aboutButton.setPadding(
                PageViewUtils.dp(activity, 14),
                PageViewUtils.dp(activity, 10),
                PageViewUtils.dp(activity, 14),
                PageViewUtils.dp(activity, 10));
        aboutButton.setOnClickListener(v -> activity.openPage(MainActivity.Page.ABOUT));
        card.addView(aboutButton, PageViewUtils.wrapWrapWithTop(activity, 16));
        return card;
    }

    private static View buildEntryCard(
            MainActivity activity,
            String monogram,
            String titleText,
            String summaryText,
            int accentColor,
            int accentBackground,
            View.OnClickListener listener) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(
                PageViewUtils.dp(activity, 16),
                PageViewUtils.dp(activity, 16),
                PageViewUtils.dp(activity, 16),
                PageViewUtils.dp(activity, 16));
        card.setMinimumHeight(PageViewUtils.dp(activity, 92));
        card.setBackground(buildOutlinedBackground(
                tintSurfaceColor(accentBackground),
                accentColor,
                18,
                activity));
        card.setOnClickListener(listener);

        TextView badge = new TextView(activity);
        badge.setText(monogram);
        badge.setTextColor(accentColor);
        badge.setTextSize(14);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(buildSolidBackground(accentBackground, 14, activity));
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                PageViewUtils.dp(activity, 44),
                PageViewUtils.dp(activity, 44));
        card.addView(badge, badgeLp);

        LinearLayout textGroup = new LinearLayout(activity);
        textGroup.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f);
        textLp.leftMargin = PageViewUtils.dp(activity, 14);

        TextView title = new TextView(activity);
        title.setText(titleText);
        title.setTextColor(activity.textColor());
        title.setTextSize(17);
        textGroup.addView(title, PageViewUtils.matchWrap());

        TextView summary = new TextView(activity);
        summary.setText(summaryText);
        summary.setTextColor(activity.subtextColor());
        summary.setTextSize(13);
        summary.setPadding(0, PageViewUtils.dp(activity, 4), 0, 0);
        textGroup.addView(summary, PageViewUtils.matchWrap());
        card.addView(textGroup, textLp);

        TextView enterChip = buildChip(activity, "进入", accentColor, 0x14FFFFFF);
        enterChip.setPadding(
                PageViewUtils.dp(activity, 10),
                PageViewUtils.dp(activity, 6),
                PageViewUtils.dp(activity, 10),
                PageViewUtils.dp(activity, 6));
        LinearLayout.LayoutParams enterLp = PageViewUtils.wrapWrap();
        enterLp.rightMargin = PageViewUtils.dp(activity, 10);
        card.addView(enterChip, enterLp);

        TextView arrow = new TextView(activity);
        arrow.setText("›");
        arrow.setTextColor(accentColor);
        arrow.setTextSize(24);
        card.addView(arrow, PageViewUtils.wrapWrap());
        return card;
    }

    private static View buildFooterCard(MainActivity activity) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(
                PageViewUtils.dp(activity, 16),
                PageViewUtils.dp(activity, 16),
                PageViewUtils.dp(activity, 16),
                PageViewUtils.dp(activity, 16));
        card.setBackground(buildOutlinedBackground(
                activity.featureSurfaceColor(),
                activity.featureStrokeColor(),
                18,
                activity));

        TextView scopeChip = buildChip(
                activity,
                "LSPosed 作用域：" + activity.supportedScopesSummary(),
                activity.primaryColor(),
                0x14FFFFFF);
        card.addView(scopeChip, PageViewUtils.matchWrap());

        TextView version = new TextView(activity);
        version.setText("版本 " + BuildConfig.VERSION_NAME + "  ·  构建 " + BuildConfig.BUILD_DATE);
        version.setTextColor(activity.textColor());
        version.setTextSize(14);
        version.setPadding(0, PageViewUtils.dp(activity, 12), 0, 0);
        card.addView(version, PageViewUtils.matchWrap());

        TextView summary = new TextView(activity);
        summary.setText("当前首页展示的是静态构建信息与目标作用域说明，不额外声明运行时激活状态。");
        summary.setTextColor(activity.subtextColor());
        summary.setTextSize(13);
        summary.setPadding(0, PageViewUtils.dp(activity, 6), 0, 0);
        card.addView(summary, PageViewUtils.matchWrap());
        return card;
    }

    private static TextView buildChip(MainActivity activity, String text, int textColor, int backgroundColor) {
        TextView chip = new TextView(activity);
        chip.setText(text);
        chip.setTextColor(textColor);
        chip.setTextSize(12);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(
                PageViewUtils.dp(activity, 12),
                PageViewUtils.dp(activity, 6),
                PageViewUtils.dp(activity, 12),
                PageViewUtils.dp(activity, 6));
        chip.setBackground(buildSolidBackground(backgroundColor, 999, activity));
        return chip;
    }

    private static View buildSectionLabel(MainActivity activity, String text) {
        TextView label = new TextView(activity);
        label.setText(text);
        label.setTextColor(activity.primaryColor());
        label.setTextSize(13);
        return label;
    }

    private static GradientDrawable buildHeroBackground(MainActivity activity) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{activity.primaryContainerColor(), activity.primaryColor(), activity.primaryDeepColor()});
        drawable.setCornerRadius(PageViewUtils.dp(activity, 24));
        return drawable;
    }

    private static GradientDrawable buildSolidBackground(int color, int radiusDp, MainActivity activity) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(PageViewUtils.dp(activity, radiusDp));
        return drawable;
    }

    private static GradientDrawable buildOutlinedBackground(
            int background,
            int stroke,
            int radiusDp,
            MainActivity activity) {
        GradientDrawable drawable = buildSolidBackground(background, radiusDp, activity);
        drawable.setStroke(PageViewUtils.dp(activity, 1), stroke);
        return drawable;
    }

    private static int tintSurfaceColor(int accentBackground) {
        int red = (255 * 9 + Color.red(accentBackground)) / 10;
        int green = (255 * 9 + Color.green(accentBackground)) / 10;
        int blue = (255 * 9 + Color.blue(accentBackground)) / 10;
        return Color.rgb(red, green, blue);
    }
}
