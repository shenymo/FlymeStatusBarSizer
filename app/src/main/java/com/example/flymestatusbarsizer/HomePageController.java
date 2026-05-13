package com.example.flymestatusbarsizer;

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
                "图标与电池",
                "图标缩放、电池样式、通知图标和信号接管",
                activity.primaryColor(),
                0x1A005CAE,
                v -> activity.openPage(MainActivity.Page.ICONS_BATTERY)),
                PageViewUtils.matchWrapWithTop(activity, 12));
        root.addView(buildEntryCard(activity,
                "时间与网络",
                "实时网速阈值、时间表达式和时间字体",
                activity.secondaryColor(),
                0x1A006688,
                v -> activity.openPage(MainActivity.Page.TIME_NETWORK)),
                PageViewUtils.matchWrapWithTop(activity, 12));
        root.addView(buildEntryCard(activity,
                "系统交互",
                "MBack 长触、导航栏沉浸和输入法控制栏",
                activity.tertiaryColor(),
                0x1A964500,
                v -> activity.openPage(MainActivity.Page.SYSTEM_INTERACTION)),
                PageViewUtils.matchWrapWithTop(activity, 12));
        root.addView(buildEntryCard(activity,
                "高级与调试",
                "配置管理、SystemUI 操作、布局微调和 Telephony 调试",
                activity.errorColor(),
                0x1ABA1A1A,
                v -> activity.openPage(MainActivity.Page.ADVANCED_DEBUG)),
                PageViewUtils.matchWrapWithTop(activity, 12));
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

        TextView title = new TextView(activity);
        title.setText("FlymeStatusBarSizer");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        card.addView(title, PageViewUtils.matchWrap());

        TextView aboutButton = buildChip(activity, "关于与支持", Color.WHITE, activity.primaryDeepColor());
        aboutButton.setPadding(
                PageViewUtils.dp(activity, 14),
                PageViewUtils.dp(activity, 10),
                PageViewUtils.dp(activity, 14),
                PageViewUtils.dp(activity, 10));
        activity.setTapClickListener(aboutButton, v -> activity.openPage(MainActivity.Page.ABOUT));

        TextView restartButton = buildChip(activity, "重启 SystemUI", Color.WHITE, 0x26FFFFFF);
        restartButton.setPadding(
                PageViewUtils.dp(activity, 14),
                PageViewUtils.dp(activity, 10),
                PageViewUtils.dp(activity, 14),
                PageViewUtils.dp(activity, 10));
        activity.setTapClickListener(restartButton, v -> activity.restartSystemUi());

        LinearLayout actionRow = new LinearLayout(activity);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        actionRow.addView(aboutButton, PageViewUtils.wrapWrap());
        LinearLayout.LayoutParams restartLp = PageViewUtils.wrapWrap();
        restartLp.leftMargin = PageViewUtils.dp(activity, 10);
        actionRow.addView(restartButton, restartLp);
        card.addView(actionRow, PageViewUtils.wrapWrapWithTop(activity, 18));
        return card;
    }

    private static View buildEntryCard(
            MainActivity activity,
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

        LinearLayout textGroup = new LinearLayout(activity);
        textGroup.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f);

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
