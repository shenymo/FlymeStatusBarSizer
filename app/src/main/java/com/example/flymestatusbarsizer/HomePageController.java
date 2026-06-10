package com.example.flymestatusbarsizer;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

final class HomePageController {
    private HomePageController() {
    }

    static void bind(MainActivity activity, LinearLayout root) {
        root.setPadding(
                root.getPaddingLeft(),
                PageViewUtils.dp(activity, 8) + activity.statusBarInset(),
                root.getPaddingRight(),
                root.getPaddingBottom());
        root.addView(buildHeroCard(activity), PageViewUtils.matchWrap());
        root.addView(buildEntryCard(activity,
                "图标与电池",
                "图标缩放、电池样式、通知图标和信号接管",
                activity.primaryColor(),
                v -> activity.openPage(MainActivity.Page.ICONS_BATTERY)),
                PageViewUtils.matchWrapWithTop(activity, 12));
        root.addView(buildEntryCard(activity,
                "时间与网络",
                "实时网速阈值、时间表达式、时间交互和时间字体",
                activity.secondaryColor(),
                v -> activity.openPage(MainActivity.Page.TIME_NETWORK)),
                PageViewUtils.matchWrapWithTop(activity, 12));
        root.addView(buildEntryCard(activity,
                "系统外观",
                "Flyme 桌面文件夹和系统外观细节",
                activity.tertiaryColor(),
                v -> activity.openPage(MainActivity.Page.SYSTEM_APPEARANCE)),
                PageViewUtils.matchWrapWithTop(activity, 12));
        root.addView(buildEntryCard(activity,
                "系统交互",
                "MBack 长触、导航栏沉浸和输入法控制栏",
                activity.primaryDeepColor(),
                v -> activity.openPage(MainActivity.Page.SYSTEM_INTERACTION)),
                PageViewUtils.matchWrapWithTop(activity, 12));
        root.addView(buildEntryCard(activity,
                "高级与调试",
                "配置管理、SystemUI 操作、布局微调和 Telephony 调试",
                activity.errorColor(),
                v -> activity.openPage(MainActivity.Page.ADVANCED_DEBUG)),
                PageViewUtils.matchWrapWithTop(activity, 12));
        root.addView(buildEntryCard(activity,
                "关于与支持",
                "项目地址、交流群、版本构建信息和目标作用域说明",
                activity.primaryDeepColor(),
                v -> activity.openPage(MainActivity.Page.ABOUT)),
                PageViewUtils.matchWrapWithTop(activity, 12));
    }

    private static View buildHeroCard(MainActivity activity) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(
                PageViewUtils.dp(activity, 16),
                PageViewUtils.dp(activity, 18),
                PageViewUtils.dp(activity, 16),
                PageViewUtils.dp(activity, 16));
        card.setBackground(activity.roundRect(activity.primaryColor(), 16));

        TextView title = new TextView(activity);
        title.setText("FlymeBarSizer");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        card.addView(title, PageViewUtils.matchWrap());

        TextView subtitle = new TextView(activity);
        subtitle.setText("状态栏尺寸与系统界面工具");
        subtitle.setTextColor(0xFFEADDFF);
        subtitle.setTextSize(14);
        subtitle.setPadding(0, PageViewUtils.dp(activity, 4), 0, 0);
        card.addView(subtitle, PageViewUtils.matchWrap());

        LinearLayout buttonRow = new LinearLayout(activity);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView restartButton = activity.filledButton("重启 SystemUI", Color.WHITE, activity.primaryColor());
        activity.setTapClickListener(restartButton, v -> activity.restartSystemUi());
        buttonRow.addView(restartButton, PageViewUtils.wrapWrap());

        ImageView donationButton = new ImageView(activity);
        donationButton.setImageResource(R.drawable.ic_donate_copy);
        donationButton.setContentDescription("捐赠");
        donationButton.setScaleType(ImageView.ScaleType.CENTER);
        donationButton.setPadding(
                PageViewUtils.dp(activity, 10),
                PageViewUtils.dp(activity, 10),
                PageViewUtils.dp(activity, 10),
                PageViewUtils.dp(activity, 10));
        donationButton.setBackground(activity.roundRect(Color.WHITE, 8));
        activity.setTapClickListener(donationButton, v -> activity.openPage(MainActivity.Page.DONATION));
        LinearLayout.LayoutParams donationLp = new LinearLayout.LayoutParams(
                PageViewUtils.dp(activity, 40),
                PageViewUtils.dp(activity, 40));
        donationLp.leftMargin = PageViewUtils.dp(activity, 10);
        buttonRow.addView(donationButton, donationLp);

        card.addView(buttonRow, PageViewUtils.wrapWrapWithTop(activity, 18));
        return card;
    }

    private static View buildEntryCard(
            MainActivity activity,
            String titleText,
            String summaryText,
            int accentColor,
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

        card.setBackground(activity.outlinedRect(activity.surfaceColor(), activity.strokeColor(), 1, 16));
        activity.setTapClickListener(card, listener);

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
        card.addView(textGroup, textLp);

        activity.addHelpButton(card, titleText, summaryText);

        TextView arrow = new TextView(activity);
        arrow.setText("›");
        arrow.setTextColor(accentColor);
        arrow.setTextSize(24);
        card.addView(arrow, PageViewUtils.wrapWrap());
        return card;
    }
}
