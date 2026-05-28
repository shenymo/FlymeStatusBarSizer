package com.example.flymestatusbarsizer;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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
                root.getPaddingTop() + activity.statusBarInset(),
                root.getPaddingRight(),
                root.getPaddingBottom());
        root.addView(buildHeroCard(activity), PageViewUtils.matchWrap());
        root.addView(buildEntryCard(activity,
                "图标与电池",
                "图标缩放、电池样式、通知图标和信号接管",
                activity.primaryColor(),
                0x1A005CAE,
                v -> activity.openPage(MainActivity.Page.ICONS_BATTERY)),
                PageViewUtils.matchWrapWithTop(activity, 12));
        root.addView(buildEntryCard(activity,
                "时间与网络",
                "实时网速阈值、时间表达式、时间交互和时间字体",
                activity.secondaryColor(),
                0x1A006688,
                v -> activity.openPage(MainActivity.Page.TIME_NETWORK)),
                PageViewUtils.matchWrapWithTop(activity, 12));
        root.addView(buildEntryCard(activity,
                "系统外观",
                "Flyme 桌面文件夹和系统外观细节",
                activity.tertiaryColor(),
                0x1A964500,
                v -> activity.openPage(MainActivity.Page.SYSTEM_APPEARANCE)),
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
        root.addView(buildEntryCard(activity,
                "关于与支持",
                "项目地址、交流群、版本构建信息和目标作用域说明",
                activity.primaryDeepColor(),
                0x1A003B73,
                v -> activity.openPage(MainActivity.Page.ABOUT)),
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
        title.setText("FlymeBarSizer");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        card.addView(title, PageViewUtils.matchWrap());

        LinearLayout buttonRow = new LinearLayout(activity);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.CENTER_VERTICAL);

        // 重启按钮升级为精致半透明玻璃态
        TextView restartButton = buildChip(activity, "重启 SystemUI", Color.WHITE, 0x33FFFFFF);
        restartButton.setPadding(
                PageViewUtils.dp(activity, 14),
                PageViewUtils.dp(activity, 10),
                PageViewUtils.dp(activity, 14),
                PageViewUtils.dp(activity, 10));
        restartButton.setBackground(buildGlassBackground(0x33FFFFFF, 0x4DFFFFFF, 999, activity));
        activity.setTapClickListener(restartButton, v -> activity.restartSystemUi());
        buttonRow.addView(restartButton, PageViewUtils.wrapWrap());

        // 捐赠按钮也升级为圆角高透玻璃态
        ImageView donationButton = new ImageView(activity);
        donationButton.setImageResource(R.drawable.ic_donate_copy);
        donationButton.setContentDescription("捐赠");
        donationButton.setScaleType(ImageView.ScaleType.CENTER);
        donationButton.setPadding(
                PageViewUtils.dp(activity, 10),
                PageViewUtils.dp(activity, 10),
                PageViewUtils.dp(activity, 10),
                PageViewUtils.dp(activity, 10));
        donationButton.setBackground(buildGlassBackground(0x33FFFFFF, 0x4DFFFFFF, 999, activity));
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
            int accentBackground,
            View.OnClickListener listener) {
        boolean isDark = (activity.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(
                PageViewUtils.dp(activity, 16),
                PageViewUtils.dp(activity, 16),
                PageViewUtils.dp(activity, 16),
                PageViewUtils.dp(activity, 16));
        card.setMinimumHeight(PageViewUtils.dp(activity, 92));

        // 描边以极其柔和的半透明色彩呈现（浅色16%，暗色25%）
        int strokeColor = Color.argb(isDark ? 0x40 : 0x2A, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor));
        card.setBackground(buildOutlinedBackground(
                tintSurfaceColor(accentBackground, isDark, activity.backgroundColor()),
                strokeColor,
                18,
                activity));
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

        TextView summary = new TextView(activity);
        summary.setText(summaryText);
        summary.setTextColor(activity.subtextColor());
        summary.setTextSize(13);
        summary.setPadding(0, PageViewUtils.dp(activity, 4), 0, 0);
        textGroup.addView(summary, PageViewUtils.matchWrap());
        card.addView(textGroup, textLp);


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

    private static GradientDrawable buildHeroBackground(MainActivity activity) {
        boolean isDark = (activity.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int[] colors;
        if (isDark) {
            colors = new int[]{0xFF1E1B4B, 0xFF311042, 0xFF4C0519};
        } else {
            colors = new int[]{0xFF4F46E5, 0xFF7C3AED, 0xFFEC4899};
        }
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                colors);
        drawable.setCornerRadius(PageViewUtils.dp(activity, 28));
        return drawable;
    }

    private static GradientDrawable buildSolidBackground(int color, int radiusDp, MainActivity activity) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(PageViewUtils.dp(activity, radiusDp));
        return drawable;
    }

    private static GradientDrawable buildGlassBackground(int color, int strokeColor, int radiusDp, MainActivity activity) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(PageViewUtils.dp(activity, radiusDp));
        drawable.setStroke(PageViewUtils.dp(activity, 1), strokeColor);
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

    private static int tintSurfaceColor(int accentBackground, boolean isDark, int bgBase) {
        int baseRed = isDark ? Color.red(bgBase) : 255;
        int baseGreen = isDark ? Color.green(bgBase) : 255;
        int baseBlue = isDark ? Color.blue(bgBase) : 255;

        int red = (baseRed * 15 + Color.red(accentBackground)) / 16;
        int green = (baseGreen * 15 + Color.green(accentBackground)) / 16;
        int blue = (baseBlue * 15 + Color.blue(accentBackground)) / 16;
        return Color.rgb(red, green, blue);
    }
}
