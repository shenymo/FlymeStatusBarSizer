package com.example.flymestatusbarsizer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int REQUEST_EXPORT_CONFIG = 1001;
    private static final int REQUEST_IMPORT_CONFIG = 1002;

    private static final int MENU_IMPORT = 1;
    private static final int MENU_EXPORT = 2;
    private static final int MENU_RESET = 3;
    private static final int MENU_RESTART = 4;
    private static final String PACKAGE_SYSTEM_UI = "com.android.systemui";
    private static final Pattern CLOCK_EXPRESSION_TOKEN_PATTERN = Pattern.compile("\\{([A-Za-z0-9_]+)\\}");
    private static final String[][] CLOCK_EXPRESSION_TOKEN_ROWS = {
            {"HH", "H", "hh", "h"},
            {"mm", "ss", "ampm", "period"},
            {"week", "week_short", "week_1"},
            {"branch", "branch_alias"}
    };

    private static final int FALLBACK_BACKGROUND = Color.rgb(253, 248, 253);
    private static final int FALLBACK_SURFACE = Color.WHITE;
    private static final int FALLBACK_SURFACE_SOFT = Color.rgb(241, 236, 242);
    private static final int FALLBACK_SURFACE_STRONG = Color.rgb(232, 222, 249);
    private static final int FALLBACK_FEATURE_SURFACE = Color.rgb(245, 238, 255);
    private static final int FALLBACK_FEATURE_STROKE = Color.rgb(207, 188, 255);
    private static final int FALLBACK_TEXT = Color.rgb(28, 27, 31);
    private static final int FALLBACK_SUBTEXT = Color.rgb(73, 69, 81);
    private static final int FALLBACK_PRIMARY = Color.rgb(79, 55, 138);
    private static final int FALLBACK_PRIMARY_CONTAINER = Color.rgb(103, 80, 164);
    private static final int FALLBACK_PRIMARY_DEEP = Color.rgb(58, 43, 103);
    private static final int FALLBACK_STROKE = Color.rgb(203, 196, 210);

    private SharedPreferences prefs;
    private int colorBackground;
    private int colorSurface;
    private int colorSurfaceSoft;
    private int colorSurfaceStrong;
    private int colorFeatureSurface;
    private int colorFeatureStroke;
    private int colorText;
    private int colorSubtext;
    private int colorPrimary;
    private int colorPrimaryContainer;
    private int colorPrimaryDeep;
    private int colorStroke;
    private RightIconGroupPreviewView previewView;
    private View[] mainPages;
    private TextView[] mainTabs;
    private final ArrayList<String> imeToolbarDraftOrder = new ArrayList<>();
    private final ArrayList<String> clockExpressionDraftTokens = new ArrayList<>();
    private final HashMap<String, TextView> clockExpressionButtons = new HashMap<>();
    private LinearLayout imeToolbarOrderContainer;
    private LinearLayout clockExpressionOrderContainer;
    private TextView clockExpressionPreviewView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = SettingsStore.prefs(this);
        SettingsStore.prepareRemoteSync(this);
        initPalette();
        configureSystemBars();
        int topInset = getStatusBarInset();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(true);
        }

        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(colorBackground);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(colorBackground);
        scrollView.setFillViewport(true);
        page.addView(scrollView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16) + topInset, dp(20), dp(120));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        root.addView(buildTopBar(), matchWrap());
        root.addView(buildMainPageContainer(), matchWrapWithTop(18));

        page.addView(buildBottomNavigation(), bottomNavigationLayoutParams());
        page.addView(buildFloatingMenuButton(), floatingMenuLayoutParams(topInset));
        setContentView(page);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        if (requestCode == REQUEST_EXPORT_CONFIG) {
            exportConfig(uri);
        } else if (requestCode == REQUEST_IMPORT_CONFIG) {
            importConfig(uri);
        }
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);

        TextView eyebrow = new TextView(this);
        eyebrow.setText("Status Bar Lab");
        eyebrow.setTextColor(colorPrimary);
        eyebrow.setTextSize(12);
        left.addView(eyebrow, matchWrap());

        TextView title = new TextView(this);
        title.setText("Flyme \u72b6\u6001\u680f");
        title.setTextColor(colorText);
        title.setTextSize(28);
        left.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("\u4e3b\u9875\u8c03\u53c2\uff0c\u6742\u9879\u9875\u653e MBack \u548c\u65f6\u95f4\u76f8\u5173\u914d\u7f6e\uff0c\u5173\u4e8e\u9875\u67e5\u7248\u672c\u4e0e\u8c03\u8bd5\u5165\u53e3");
        subtitle.setTextColor(colorSubtext);
        subtitle.setTextSize(14);
        subtitle.setPadding(0, dp(4), 0, 0);
        left.addView(subtitle, matchWrap());
        bar.addView(left, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return bar;
    }

    private View buildMainPageContainer() {
        FrameLayout pageContainer = new FrameLayout(this);
        LinearLayout homePage = buildHomePage();
        LinearLayout debugPage = buildDebugPage();
        LinearLayout aboutPage = buildAboutPage();
        pageContainer.addView(homePage, matchWrapFrame());
        pageContainer.addView(debugPage, matchWrapFrame());
        pageContainer.addView(aboutPage, matchWrapFrame());
        mainPages = new View[]{homePage, debugPage, aboutPage};
        return pageContainer;
    }

    private View buildBottomNavigation() {
        LinearLayout shell = card(colorSurface, 26);
        shell.setOrientation(LinearLayout.HORIZONTAL);
        shell.setPadding(dp(12), dp(12), dp(12), dp(12));
        shell.setElevation(dp(8));

        TextView homeTab = buildBottomNavTab("主页");
        TextView miscTab = buildBottomNavTab("杂项");
        TextView aboutTab = buildBottomNavTab("关于");
        shell.addView(homeTab, weightedWrap());
        shell.addView(miscTab, weightedWrapWithStart(10));
        shell.addView(aboutTab, weightedWrapWithStart(10));

        mainTabs = new TextView[]{homeTab, miscTab, aboutTab};
        bindMainTabs(0);
        homeTab.setOnClickListener(v -> bindMainTabs(0));
        miscTab.setOnClickListener(v -> bindMainTabs(1));
        aboutTab.setOnClickListener(v -> bindMainTabs(2));
        return shell;
    }

    private LinearLayout buildHomePage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);

        page.addView(buildPreviewPlaceholder(), matchWrap());
        page.addView(buildIntroCard(), matchWrapWithTop(16));
        page.addView(buildStatusBarIconScaleCard(), matchWrapWithTop(16));

        addSectionLabel(page, "实时网速");
        page.addView(buildConnectionRateCard(), matchWrapWithTop(10));

        addSectionLabel(page, "右上角图标组");
        page.addView(buildRightIconGroupSection(), matchWrapWithTop(10));
        return page;
    }

    private LinearLayout buildDebugPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);

        addSectionLabel(page, "杂项");
        page.addView(buildNotificationCard(), matchWrapWithTop(10));
        page.addView(buildImeToolbarCard(), matchWrapWithTop(10));
        page.addView(buildMBackSection(), matchWrapWithTop(10));
        page.addView(buildTimeCard(), matchWrapWithTop(10));
        return page;
    }

    private View buildNotificationCard() {
        LinearLayout card = card(colorSurface, 28);
        addProfileSectionHeader(card, "通知",
                "只接管 SystemUI 通知卡片的背景层。Android 13 及以上叠加液态玻璃采样层，低版本回退为透明背景。");
        addSwitchRow(card, "通知液态玻璃",
                "开启后会把通知卡片背景替换成液态玻璃效果，并清掉原来的 tint。文字、图标和点击区域保持原来的 SystemUI 行为。",
                SettingsStore.KEY_NOTIFICATION_BACKGROUND_TRANSPARENT,
                SettingsStore.DEFAULT_NOTIFICATION_BACKGROUND_TRANSPARENT);
        return buildExpandableInfoCard(
                "通知卡片",
                "给通知卡片补一个液态玻璃背景开关，Android 13+ 使用 shader 采样通知后方内容，低版本自动退回透明卡片。",
                "通知", card);
    }

    private View buildImeToolbarCard() {
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);

        addProfileSectionHeader(details, "输入法工具栏",
                "在输入法内容区下方补一排常用按钮，包含粘贴、删除、全选、复制和切换输入法。");
        addSwitchRow(details, "启用输入法工具栏",
                "开启后会在输入法界面加一排工具按钮。关闭后恢复原来的输入法视图，不再显示这排按钮。",
                SettingsStore.KEY_IME_TOOLBAR_ENABLED,
                SettingsStore.DEFAULT_IME_TOOLBAR_ENABLED);
        addDivider(details);
        addProfileSectionHeader(details, "按钮顺序",
                "长按某个按钮项后拖到目标位置，再点应用。这里只改五个按钮的左右顺序，不改按钮功能。");

        TextView hint = new TextView(this);
        hint.setText("按住列表项拖动，松手后会插到对应位置。");
        hint.setTextColor(colorSubtext);
        hint.setTextSize(13);
        hint.setPadding(0, dp(10), 0, 0);
        details.addView(hint, matchWrap());

        imeToolbarOrderContainer = new LinearLayout(this);
        imeToolbarOrderContainer.setOrientation(LinearLayout.VERTICAL);
        imeToolbarOrderContainer.setPadding(0, dp(12), 0, 0);
        details.addView(imeToolbarOrderContainer, matchWrap());
        loadImeToolbarDraftOrder();
        renderImeToolbarOrderEditor();

        addDivider(details);
        addActionButtonRow(details, "应用当前顺序",
                "保存这五个按钮的新顺序，并通知当前输入法界面马上刷新。",
                "应用", this::applyImeToolbarOrder);
        return buildExpandableInfoCard(
                "输入法工具栏",
                "可以控制输入法工具栏开关，也可以拖动调整这五个按钮的顺序。",
                "工具栏", details);
    }

    private LinearLayout buildAboutPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);

        addSectionLabel(page, "关于");

        LinearLayout card = card(colorSurface, 28);

        TextView title = new TextView(this);
        title.setText("Flyme Status Bar Sizer");
        title.setTextColor(colorText);
        title.setTextSize(20);
        card.addView(title, matchWrap());

        TextView summary = new TextView(this);
        summary.setText("Flyme 状态栏电池图标替换模块。");
        summary.setTextColor(colorSubtext);
        summary.setTextSize(14);
        summary.setPadding(0, dp(6), 0, 0);
        card.addView(summary, matchWrap());

        TextView githubTitle = new TextView(this);
        githubTitle.setText("GitHub");
        githubTitle.setTextColor(colorText);
        githubTitle.setTextSize(15);
        githubTitle.setPadding(0, dp(16), 0, 0);
        card.addView(githubTitle, matchWrap());

        TextView githubLink = new TextView(this);
        githubLink.setText("https://github.com/shenymo/FlymeStatusBarSizer");
        githubLink.setTextColor(colorPrimary);
        githubLink.setTextSize(14);
        githubLink.setSingleLine(false);
        githubLink.setEllipsize(TextUtils.TruncateAt.END);
        githubLink.setPadding(0, dp(6), 0, 0);
        githubLink.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://github.com/shenymo/FlymeStatusBarSizer"))));
        card.addView(githubLink, matchWrap());

        final String groupLinkUrl = "https://qun.qq.com/universal-share/share?ac=1&authKey=WuaHYIEHdI6Y%2Fvn7SvcFMtyuUX%2Bwp%2FMedY0eMgPLq9Bbrz%2FPMRsiIgDttNOMbPWW&busi_data=eyJncm91cENvZGUiOiIxMTAyMTM4MzgxIiwidG9rZW4iOiJIb1hmV2xvaVUxWFk2YjAyOXl5MmIwelljU3A5bFRYejQrb3JtUlJwOXRMK1BLU3pnWWRaSG9VdHZ4M3Fld2xqIiwidWluIjoiMjI4OTU3MTk5MCJ9&data=O3ClX619ry0x93elARpxRoHiwSavPU_N00zhT1jj5d_rR0feICi-g7gudqIpU6sbrKtr1_CCPBpNQ-APojGliw&svctype=4&tempid=h5_group_info";
        final String groupNumber = "1102138381";
        TextView groupLink = new TextView(this);
        String groupText = "交流群：" + groupNumber;
        SpannableString groupSpan = new SpannableString(groupText);
        int groupNumberStart = groupText.indexOf(groupNumber);
        if (groupNumberStart >= 0) {
            groupSpan.setSpan(new ClickableSpan() {
                @Override
                public void onClick(View widget) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(groupLinkUrl)));
                }

                @Override
                public void updateDrawState(TextPaint ds) {
                    super.updateDrawState(ds);
                    ds.setColor(colorPrimary);
                    ds.setUnderlineText(false);
                }
            }, groupNumberStart, groupNumberStart + groupNumber.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        groupLink.setText(groupSpan);
        groupLink.setTextColor(colorSubtext);
        groupLink.setTextSize(15);
        groupLink.setSingleLine(false);
        groupLink.setEllipsize(TextUtils.TruncateAt.END);
        groupLink.setPadding(0, dp(16), 0, 0);
        groupLink.setMovementMethod(LinkMovementMethod.getInstance());
        groupLink.setHighlightColor(Color.TRANSPARENT);
        card.addView(groupLink, matchWrap());

        TextView versionTitle = new TextView(this);
        versionTitle.setText("当前版本");
        versionTitle.setTextColor(colorText);
        versionTitle.setTextSize(15);
        versionTitle.setPadding(0, dp(16), 0, 0);
        card.addView(versionTitle, matchWrap());

        TextView versionValue = new TextView(this);
        versionValue.setText(BuildConfig.VERSION_NAME);
        versionValue.setTextColor(colorSubtext);
        versionValue.setTextSize(14);
        versionValue.setPadding(0, dp(6), 0, 0);
        card.addView(versionValue, matchWrap());

        TextView buildDateTitle = new TextView(this);
        buildDateTitle.setText("构建日期");
        buildDateTitle.setTextColor(colorText);
        buildDateTitle.setTextSize(15);
        buildDateTitle.setPadding(0, dp(16), 0, 0);
        card.addView(buildDateTitle, matchWrap());

        TextView buildDateValue = new TextView(this);
        buildDateValue.setText(BuildConfig.BUILD_DATE);
        buildDateValue.setTextColor(colorSubtext);
        buildDateValue.setTextSize(14);
        buildDateValue.setPadding(0, dp(6), 0, 0);
        card.addView(buildDateValue, matchWrap());

        page.addView(card, matchWrapWithTop(10));
        return page;
    }

    private View buildFloatingMenuButton() {
        TextView button = new TextView(this);
        button.setText("\u83dc\u5355");
        button.setTextColor(Color.WHITE);
        button.setTextSize(14);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(18), dp(12), dp(18), dp(12));
        button.setBackground(roundRect(colorPrimary, 999));
        button.setElevation(dp(6));
        button.setOnClickListener(this::showTopMenu);
        return button;
    }

    private View buildPreviewPlaceholder() {
        FrameLayout card = new FrameLayout(this);
        card.setMinimumHeight(dp(220));
        card.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(220) + dp(48)));
        card.setPadding(dp(24), dp(24), dp(24), dp(24));
        card.setBackground(gradientCard());

        previewView = new RightIconGroupPreviewView(this);
        previewView.setMinimumHeight(dp(220));
        previewView.setPreviewTintColor(colorText);
        previewView.setBatteryStyle(readIntSetting(
                SettingsStore.KEY_BATTERY_ICON_STYLE,
                SettingsStore.DEFAULT_BATTERY_ICON_STYLE));
        previewView.setBatteryTextFont(readIntSetting(
                SettingsStore.KEY_BATTERY_TEXT_FONT,
                SettingsStore.DEFAULT_BATTERY_TEXT_FONT));
        previewView.setIconScalePercent(readIntSetting(
                SettingsStore.KEY_STATUS_BAR_ICON_SCALE_PERCENT,
                SettingsStore.DEFAULT_STATUS_BAR_ICON_SCALE_PERCENT));
        previewView.setBatteryInnerTextScalePercent(readIntSetting(
                SettingsStore.KEY_BATTERY_INNER_TEXT_SCALE_PERCENT,
                SettingsStore.DEFAULT_BATTERY_INNER_TEXT_SCALE_PERCENT));
        previewView.setBatteryLevelTextEnabled(SettingsStore.readBoolean(
                prefs,
                SettingsStore.KEY_BATTERY_LEVEL_TEXT_ENABLED,
                SettingsStore.DEFAULT_BATTERY_LEVEL_TEXT_ENABLED));
        previewView.setBatteryHollowEnabled(SettingsStore.readBoolean(
                prefs,
                SettingsStore.KEY_BATTERY_HOLLOW_ENABLED,
                SettingsStore.DEFAULT_BATTERY_HOLLOW_ENABLED));
        previewView.setBatteryHollowFillFollowsLevel(SettingsStore.readBoolean(
                prefs,
                SettingsStore.KEY_BATTERY_HOLLOW_FILL_FOLLOWS_LEVEL,
                SettingsStore.DEFAULT_BATTERY_HOLLOW_FILL_FOLLOWS_LEVEL));
        FrameLayout.LayoutParams previewLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(220));
        previewLp.gravity = Gravity.TOP;
        card.addView(previewView, previewLp);

        TextView badge = chip("\u5b9e\u65f6\u9884\u89c8", Color.argb(55, 255, 255, 255), Color.WHITE);
        FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        badgeLp.gravity = Gravity.TOP | Gravity.START;
        card.addView(badge, badgeLp);
        return card;
    }

    private View buildIntroCard() {
        LinearLayout card = card(colorSurface, 24);

        TextView title = new TextView(this);
        title.setText("\u4f7f\u7528\u8bf4\u660e");
        title.setTextColor(colorText);
        title.setTextSize(18);
        card.addView(title, matchWrap());

        TextView summary = new TextView(this);
        summary.setText("\u8bbe\u7f6e\u4f1a\u76f4\u63a5\u5199\u5165\u6a21\u5757\u914d\u7f6e\uff0cSystemUI \u91cd\u542f\u540e\u751f\u6548\u66f4\u7a33\u5b9a\u3002\u73b0\u5728\u9884\u89c8\u533a\u5df2\u52a0\u4e0a\u5355\u5361\u548c\u53cc\u5361\u5408\u4e00\u4e24\u79cd\u4fe1\u53f7\u56fe\u6807\u8349\u56fe\uff0c\u4fbf\u4e8e\u5148\u770b\u6bd4\u4f8b\u3002");
        summary.setTextColor(colorSubtext);
        summary.setTextSize(14);
        summary.setPadding(0, dp(6), 0, 0);
        card.addView(summary, matchWrap());
        return card;
    }

    private View buildStatusBarIconScaleCard() {
        LinearLayout card = card(colorSurface, 24);

        TextView title = new TextView(this);
        title.setText("状态栏图标大小");
        title.setTextColor(colorText);
        title.setTextSize(18);
        card.addView(title, matchWrap());

        TextView summary = new TextView(this);
        summary.setText("统一控制右上角系统状态图标，以及代码绘制开启后的电池图标和信号图标。通知图标、隐私权限标识和隐私圆点不在这里面。");
        summary.setTextColor(colorSubtext);
        summary.setTextSize(14);
        summary.setPadding(0, dp(6), 0, 0);
        card.addView(summary, matchWrap());

        addDivider(card);
        addSliderRow(card, "全部状态栏图标大小",
                "默认 100%。统一调右上角系统状态图标，还有代码绘制的电池和信号图标。",
                SettingsStore.KEY_STATUS_BAR_ICON_SCALE_PERCENT,
                SettingsStore.DEFAULT_STATUS_BAR_ICON_SCALE_PERCENT, 50, 200, "%");
        addDivider(card);
        addSliderRow(card, "电池内部数字大小",
                "只改电池图标内部的电量数字。默认 100%。",
                SettingsStore.KEY_BATTERY_INNER_TEXT_SCALE_PERCENT,
                SettingsStore.DEFAULT_BATTERY_INNER_TEXT_SCALE_PERCENT, 50, 200, "%");
        return card;
    }

    private View buildConnectionRateCard() {
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);

        details.addView(buildConnectionRateThresholdPage(), matchWrap());

        return buildExpandableInfoCard(
                "实时网速",
                "保留系统原采样，只保留显隐阈值和确认次数。",
                "网速", details);
    }

    private View buildMBackSection() {
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);

        TextView pageHint = new TextView(this);
        pageHint.setText("分成 3 页来调，先定长触动作，再试沉浸/inset，最后单独压导航栏高度");
        pageHint.setTextColor(colorSubtext);
        pageHint.setTextSize(13);
        details.addView(pageHint, matchWrap());

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(0, dp(14), 0, 0);
        TextView actionTab = buildSettingsPageTab("长触动作");
        TextView immersiveTab = buildSettingsPageTab("沉浸 / Inset");
        TextView heightTab = buildSettingsPageTab("导航栏高度");
        tabs.addView(actionTab, weightedWrap());
        tabs.addView(immersiveTab, weightedWrapWithStart(10));
        tabs.addView(heightTab, weightedWrapWithStart(10));
        details.addView(tabs, matchWrap());

        FrameLayout pageContainer = new FrameLayout(this);
        pageContainer.setPadding(0, dp(16), 0, 0);
        LinearLayout actionPage = buildMBackActionPage();
        LinearLayout immersivePage = buildMBackImmersivePage();
        LinearLayout heightPage = buildMBackHeightPage();
        pageContainer.addView(actionPage, matchWrapFrame());
        pageContainer.addView(immersivePage, matchWrapFrame());
        pageContainer.addView(heightPage, matchWrapFrame());
        details.addView(pageContainer, matchWrap());

        View[] pages = new View[]{actionPage, immersivePage, heightPage};
        TextView[] pageTabs = new TextView[]{actionTab, immersiveTab, heightTab};
        bindSettingsPageTabs(pages, pageTabs, 0);
        actionTab.setOnClickListener(v -> bindSettingsPageTabs(pages, pageTabs, 0));
        immersiveTab.setOnClickListener(v -> bindSettingsPageTabs(pages, pageTabs, 1));
        heightTab.setOnClickListener(v -> bindSettingsPageTabs(pages, pageTabs, 2));

        return buildExpandableInfoCard(
                "MBack",
                "把 mBack 长触、导航栏透明、底部 inset 和导航栏高度实验项收拢到一组，便于分阶段测试。",
                "MBack", details);
    }

    private LinearLayout buildMBackActionPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);

        addProfileSectionHeader(page, "长触动作",
                "只接管 mBack 长触分支，保留单击和系统其他来源。目标可以填 URL、自定义 scheme 或 intent://。");
        addSwitchRow(page, "接管长触 mBack",
                "拦截 Flyme SystemUI 里原本唤醒 AICY 的 mBack/Home 长触入口，改为发送模块配置的 Intent。",
                SettingsStore.KEY_MBACK_LONG_TOUCH_URL_ENABLED,
                SettingsStore.DEFAULT_MBACK_LONG_TOUCH_URL_ENABLED);
        addDivider(page);
        addTextSettingRow(page, "目标 URL / Intent URI",
                "支持 https://、自定义 scheme 和 intent:// URI。点击右侧内容编辑，留空则回退原始 AICY 行为。",
                SettingsStore.KEY_MBACK_LONG_TOUCH_INTENT_URI,
                SettingsStore.DEFAULT_MBACK_LONG_TOUCH_INTENT_URI,
                "未设置");
        addDivider(page);
        addActionButtonRow(page, "测试启动",
                "不需要长按 mBack，直接用当前配置尝试启动一次，方便先验证 URL / Intent URI 是否可用。",
                "立即测试", this::testLaunchMBackIntent);
        return page;
    }

    private LinearLayout buildMBackImmersivePage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);

        addProfileSectionHeader(page, "沉浸 / Inset",
                "优先用透明背景和底部 inset 去试应用能否绘制到 mBack 下方。透明只改背景层，inset 直接影响应用可用区域判断。");
        addSwitchRow(page, "mBack 导航栏透明",
                "把 mBack 所在导航栏背景压成透明，只动导航栏背景层，不改 mBack 本体绘制。用于测试底部白边问题。",
                SettingsStore.KEY_MBACK_NAV_BAR_TRANSPARENT,
                SettingsStore.DEFAULT_MBACK_NAV_BAR_TRANSPARENT);
        addDivider(page);
        addSwitchRow(page, "隐藏小白条",
                "只隐藏 mBack 自己画出来的那条胶囊，不直接改长触逻辑和 inset。适合配合透明背景、inset=0 一起试。",
                SettingsStore.KEY_MBACK_HIDE_PILL,
                SettingsStore.DEFAULT_MBACK_HIDE_PILL);
        addDivider(page);
        addInsetSliderRow(page, "mBack inset 大小",
                "控制 SystemUI 返回给应用的底部 inset。-1 表示保持系统默认，0 表示压成 0，其他数值按 dp 处理。实验项，可能影响部分应用底部点击区域。",
                SettingsStore.KEY_MBACK_INSET_SIZE,
                SettingsStore.DEFAULT_MBACK_INSET_SIZE, -1, 80);
        return page;
    }

    private LinearLayout buildMBackHeightPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);

        addProfileSectionHeader(page, "导航栏高度",
                "这个页直接压 mBack 导航栏窗口本身的高度，比单纯透明更能减少底部透明可触区域对应用按钮的遮挡。");
        addInsetSliderRow(page, "mBack 导航栏高度",
                "控制 mBack 导航栏窗口本身的高度。-1 表示保持系统默认，数值越小，底部透明可触区域越矮。这个项更直接影响应用底部按钮是否容易被挡住。",
                SettingsStore.KEY_MBACK_NAV_BAR_HEIGHT,
                SettingsStore.DEFAULT_MBACK_NAV_BAR_HEIGHT, -1, 80);
        return page;
    }

    private LinearLayout buildConnectionRateThresholdPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);

        addProfileSectionHeader(page, "阈值显隐",
                "显示阈值和隐藏阈值分开，连续采样确认后再切换，继续用 GONE 但尽量避免频繁抖动。");
        addSwitchRow(page, "启用阈值显隐",
                "高于显示阈值时显示，低于隐藏阈值时隐藏。只改显示，不改采样。",
                SettingsStore.KEY_CONNECTION_RATE_AUTO_VISIBILITY_ENABLED,
                SettingsStore.DEFAULT_CONNECTION_RATE_AUTO_VISIBILITY_ENABLED);
        addDivider(page);
        addSliderRow(page, "显示阈值",
                "连续达到这个速度后才显示，单位 KB/s",
                SettingsStore.KEY_CONNECTION_RATE_SHOW_THRESHOLD_KB,
                SettingsStore.DEFAULT_CONNECTION_RATE_SHOW_THRESHOLD_KB, 0, 1024, "KB/s");
        addDivider(page);
        addSliderRow(page, "隐藏阈值",
                "低于这个速度后才隐藏，单位 KB/s",
                SettingsStore.KEY_CONNECTION_RATE_HIDE_THRESHOLD_KB,
                SettingsStore.DEFAULT_CONNECTION_RATE_HIDE_THRESHOLD_KB, 0, 1024, "KB/s");
        addDivider(page);
        addSliderRow(page, "显示确认次数",
                "连续多少次达到显示阈值才真正显示",
                SettingsStore.KEY_CONNECTION_RATE_SHOW_SAMPLE_COUNT,
                SettingsStore.DEFAULT_CONNECTION_RATE_SHOW_SAMPLE_COUNT, 1, 5, "次");
        addDivider(page);
        addSliderRow(page, "隐藏确认次数",
                "连续多少次低于隐藏阈值才真正隐藏",
                SettingsStore.KEY_CONNECTION_RATE_HIDE_SAMPLE_COUNT,
                SettingsStore.DEFAULT_CONNECTION_RATE_HIDE_SAMPLE_COUNT, 1, 5, "次");
        return page;
    }

    private View buildRightIconGroupSection() {
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);

        addSwitchRow(details, "代码绘制电池图标",
                "关闭后恢复系统原来的电池图标，不再接管这一项的绘制和尺寸。",
                SettingsStore.KEY_BATTERY_CODE_DRAW_ENABLED,
                SettingsStore.DEFAULT_BATTERY_CODE_DRAW_ENABLED);
        addDivider(details);
        addChoiceRow(details, "电池图标样式",
                "当前有两套代码绘制样式：类IOS 和类ONEUI。",
                SettingsStore.KEY_BATTERY_ICON_STYLE,
                SettingsStore.DEFAULT_BATTERY_ICON_STYLE,
                new int[]{SettingsStore.BATTERY_STYLE_IOS, SettingsStore.BATTERY_STYLE_ONEUI},
                new String[]{"类IOS", "类ONEUI"});
        addDivider(details);
        addSwitchRow(details, "电池内显示电量数字",
                "关闭后只保留图形电池，不在电池内部绘制剩余电量数字。",
                SettingsStore.KEY_BATTERY_LEVEL_TEXT_ENABLED,
                SettingsStore.DEFAULT_BATTERY_LEVEL_TEXT_ENABLED);
        addDivider(details);
        LinearLayout hollowOptions = buildBatteryHollowOptions();
        hollowOptions.setVisibility(SettingsStore.readBoolean(
                prefs,
                SettingsStore.KEY_BATTERY_HOLLOW_ENABLED,
                SettingsStore.DEFAULT_BATTERY_HOLLOW_ENABLED) ? View.VISIBLE : View.GONE);
        addSwitchRow(details, "镂空电池",
                "开启后使用镂空电池样式。默认内部保持满填，下面可以单独控制是否随容量缩短。",
                SettingsStore.KEY_BATTERY_HOLLOW_ENABLED,
                SettingsStore.DEFAULT_BATTERY_HOLLOW_ENABLED,
                (buttonView, isChecked) -> hollowOptions.setVisibility(isChecked ? View.VISIBLE : View.GONE));
        LinearLayout.LayoutParams hollowOptionsLp = matchWrapWithTop(10);
        hollowOptionsLp.leftMargin = dp(12);
        details.addView(hollowOptions, hollowOptionsLp);
        addDivider(details);
        int[] batteryTextFontOptions = BatteryTextFontHelper.getAvailableFontOptions(this);
        addChoiceRow(details, "电池数字字体",
                "这里会列出系统可用字体，也包含模块自带的 MiSansLatinVFNumber。选择后预览会马上更新。",
                SettingsStore.KEY_BATTERY_TEXT_FONT,
                SettingsStore.DEFAULT_BATTERY_TEXT_FONT,
                batteryTextFontOptions,
                BatteryTextFontHelper.getFontLabels(batteryTextFontOptions));
        addDivider(details);
        addProfileSectionHeader(details, "固定绘制",
                "这里只替换系统原来的电池图标，固定使用 24dp 画布绘制。Wi-Fi 和移动信号不再接管。");

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.addView(buildExpandableInfoCard(
                "\u7535\u6c60\u56fe\u6807",
                "\u53ea\u628a\u7cfb\u7edf\u539f\u6709\u7684\u7535\u6c60\u56fe\u6807\u6539\u4e3a\u4ee3\u7801\u7ed8\u5236\uff0c\u53ef\u4ee5\u5355\u72ec\u5f00\u5173\uff0c\u4e0d\u518d\u63a5\u7ba1 Wi-Fi \u548c\u79fb\u52a8\u4fe1\u53f7\u3002",
                "\u7535\u6c60", details), matchWrap());
        container.addView(buildSignalIconSection(), matchWrapWithTop(12));
        return container;
    }

    private View buildSignalIconSection() {
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);

        addSwitchRow(details, "代码绘制信号图标",
                "关闭后恢复系统原来的信号图标，不再替换 mobile_signal，也不再改双卡槽位和图标尺寸。",
                SettingsStore.KEY_SIGNAL_CODE_DRAW_ENABLED,
                SettingsStore.DEFAULT_SIGNAL_CODE_DRAW_ENABLED);
        addDivider(details);
        addProfileSectionHeader(details, "恢复方式",
                "这个开关只决定模块还要不要接管信号图标。关闭后信号相关逻辑会停用，SystemUI 下次重启会回到系统原图标。");

        return buildExpandableInfoCard(
                "信号图标",
                "单独控制移动信号图标这一组逻辑，关闭后不再替换系统原图标。",
                "信号", details);
    }

    private TextView buildSettingsPageTab(String text) {
        TextView tab = new TextView(this);
        tab.setText(text);
        tab.setGravity(Gravity.CENTER);
        tab.setTextSize(14);
        tab.setPadding(dp(10), dp(12), dp(10), dp(12));
        tab.setBackground(roundRect(colorSurfaceSoft, 999));
        return tab;
    }

    private TextView buildBottomNavTab(String text) {
        TextView tab = new TextView(this);
        tab.setText(text);
        tab.setGravity(Gravity.CENTER);
        tab.setTextSize(15);
        tab.setPadding(dp(10), dp(12), dp(10), dp(12));
        tab.setBackground(roundRect(colorSurfaceSoft, 999));
        return tab;
    }

    private void bindSettingsPageTabs(View[] pages, TextView[] tabs, int selectedIndex) {
        for (int i = 0; i < pages.length; i++) {
            boolean selected = i == selectedIndex;
            pages[i].setVisibility(selected ? View.VISIBLE : View.GONE);
            tabs[i].setTextColor(selected ? Color.WHITE : colorPrimary);
            tabs[i].setBackground(roundRect(selected ? colorPrimary : colorSurfaceSoft, 999));
        }
    }

    private void bindMainTabs(int selectedIndex) {
        if (mainPages == null || mainTabs == null) {
            return;
        }
        bindSettingsPageTabs(mainPages, mainTabs, selectedIndex);
    }

    private View buildTimeCard() {
        LinearLayout card = card(colorSurface, 28);
        addProfileSectionHeader(card, "自定义时间表达式",
                "点下面的按钮加入表达式。已加入的项支持长按拖动排序。小时、分钟、秒连续排列时会自动补冒号，不需要单独插入。");
        TextView hint = new TextView(this);
        hint.setText("当前支持：小时、分钟、秒、星期、AM/PM、时段词、十二时辰地支和传统别称。");
        hint.setTextColor(colorSubtext);
        hint.setTextSize(13);
        hint.setPadding(0, dp(10), 0, 0);
        card.addView(hint, matchWrap());

        card.addView(buildClockExpressionButtonPanel(), matchWrapWithTop(12));

        TextView orderTitle = new TextView(this);
        orderTitle.setText("当前顺序");
        orderTitle.setTextColor(colorPrimary);
        orderTitle.setTextSize(15);
        orderTitle.setPadding(0, dp(16), 0, 0);
        card.addView(orderTitle, matchWrap());

        TextView orderHint = new TextView(this);
        orderHint.setText("点击已选项可移除，长按可拖动排序。");
        orderHint.setTextColor(colorSubtext);
        orderHint.setTextSize(12);
        orderHint.setPadding(0, dp(4), 0, 0);
        card.addView(orderHint, matchWrap());

        clockExpressionPreviewView = new TextView(this);
        clockExpressionPreviewView.setTextColor(colorPrimary);
        clockExpressionPreviewView.setTextSize(13);
        clockExpressionPreviewView.setPadding(dp(12), dp(10), dp(12), dp(10));
        clockExpressionPreviewView.setBackground(roundRect(colorSurfaceSoft, 18));
        card.addView(clockExpressionPreviewView, matchWrapWithTop(10));

        clockExpressionOrderContainer = new LinearLayout(this);
        clockExpressionOrderContainer.setOrientation(LinearLayout.VERTICAL);
        clockExpressionOrderContainer.setPadding(0, dp(12), 0, 0);
        card.addView(clockExpressionOrderContainer, matchWrap());
        loadClockExpressionDraft();
        renderClockExpressionEditor();

        addDivider(card);
        addActionButtonRow(card, "应用当前表达式",
                "保存当前按钮顺序生成的表达式，并通知 SystemUI 立即刷新状态栏时间。",
                "应用", this::applyClockExpressionDraft);
        addDivider(card);
        addActionButtonRow(card, "清空当前表达式",
                "清空后会只保留系统原始时间显示。",
                "清空", this::clearClockExpressionDraft);
        addDivider(card);
        addSwitchRow(card, "\u65f6\u95f4\u52a0\u7c97",
                "\u5bf9\u72b6\u6001\u680f\u65f6\u95f4\u4ee5\u53ca\u5176\u53f3\u4fa7\u8ffd\u52a0\u7684\u661f\u671f/\u65e5\u671f\u5e94\u7528\u5b57\u91cd",
                SettingsStore.KEY_CLOCK_BOLD_ENABLED, SettingsStore.DEFAULT_CLOCK_BOLD_ENABLED);
        addDivider(card);
        addSliderRow(card, "\u65f6\u95f4/\u65e5\u671f\u7c97\u7ec6",
                "\u53ea\u5bf9\u72b6\u6001\u680f\u65f6\u95f4\u6587\u5b57\u751f\u6548\uff0c\u8303\u56f4 100-900",
                SettingsStore.KEY_CLOCK_FONT_WEIGHT, SettingsStore.DEFAULT_CLOCK_FONT_WEIGHT, 100, 900, "");
        addDivider(card);
        addSliderRow(card, "时间和锁屏运营商字体大小",
                "同时控制左上角时间、锁屏界面运营商，以及网速显示文字大小。默认 100%。",
                SettingsStore.KEY_CLOCK_AND_CARRIER_TEXT_SIZE_PERCENT,
                SettingsStore.DEFAULT_CLOCK_AND_CARRIER_TEXT_SIZE_PERCENT, 50, 200, "%");
        return card;
    }

    private LinearLayout buildExpandableInfoCard(String titleText, String subtitleText,
            String badgeText, LinearLayout details) {
        LinearLayout card = card(colorFeatureSurface, colorFeatureStroke, 28);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);

        TextView badge = chip(badgeText, colorSurfaceStrong, colorPrimary);
        textColumn.addView(badge, matchWrap());

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(colorText);
        title.setTextSize(20);
        title.setPadding(0, dp(10), 0, 0);
        textColumn.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText(subtitleText);
        subtitle.setTextColor(colorSubtext);
        subtitle.setTextSize(14);
        subtitle.setPadding(0, dp(6), 0, 0);
        textColumn.addView(subtitle, matchWrap());

        TextView expand = new TextView(this);
        expand.setText("\u5c55\u5f00");
        expand.setTextColor(colorPrimary);
        expand.setTextSize(13);

        header.addView(textColumn, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(expand, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        details.setVisibility(View.GONE);
        details.setPadding(0, dp(16), 0, 0);

        header.setOnClickListener(v -> {
            boolean expanded = details.getVisibility() == View.VISIBLE;
            details.setVisibility(expanded ? View.GONE : View.VISIBLE);
            expand.setText(expanded ? "\u5c55\u5f00" : "\u6536\u8d77");
        });

        card.addView(header, matchWrap());
        card.addView(details, matchWrap());
        return card;
    }

    private void addSectionLabel(LinearLayout root, String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(colorPrimary);
        label.setTextSize(13);
        label.setPadding(dp(2), dp(20), 0, 0);
        root.addView(label, matchWrap());
    }

    private void addSwitchRow(LinearLayout root, String titleText, String subtitleText,
            String key, boolean defaultValue) {
        addSwitchRow(root, titleText, subtitleText, key, defaultValue, null);
    }

    private void addSwitchRow(LinearLayout root, String titleText, String subtitleText,
            String key, boolean defaultValue, CompoundButton.OnCheckedChangeListener extraListener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(colorText);
        title.setTextSize(16);
        textColumn.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText(subtitleText);
        subtitle.setTextColor(colorSubtext);
        subtitle.setTextSize(13);
        subtitle.setPadding(0, dp(4), dp(10), 0);
        textColumn.addView(subtitle, matchWrap());

        Switch toggle = new Switch(this);
        toggle.setChecked(SettingsStore.readBoolean(prefs, key, defaultValue));
        toggle.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
            putBooleanSetting(key, isChecked);
            if (extraListener != null) {
                extraListener.onCheckedChanged(buttonView, isChecked);
            }
        });

        row.addView(textColumn, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(toggle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(row, matchWrap());
    }

    private LinearLayout buildBatteryHollowOptions() {
        LinearLayout card = card(colorSurfaceSoft, colorStroke, 22);
        TextView title = new TextView(this);
        title.setText("镂空电池");
        title.setTextColor(colorPrimary);
        title.setTextSize(13);
        card.addView(title, matchWrap());
        addDivider(card);
        addSwitchRow(card, "电池内填充色随容量变化",
                "关闭时内部始终填满；开启后内部填充会按剩余电量缩短，未填充部分保留灰色底色。",
                SettingsStore.KEY_BATTERY_HOLLOW_FILL_FOLLOWS_LEVEL,
                SettingsStore.DEFAULT_BATTERY_HOLLOW_FILL_FOLLOWS_LEVEL);
        return card;
    }

    private void addSliderRow(LinearLayout root, String titleText, String subtitleText, String key,
            int defaultValue, int min, int max, String suffix) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(colorText);
        title.setTextSize(16);
        header.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView valueView = new TextView(this);
        valueView.setTextColor(colorPrimary);
        valueView.setTextSize(14);
        valueView.setPadding(dp(12), 0, 0, 0);
        int current = readIntSetting(key, defaultValue);
        int clamped = Math.max(min, Math.min(max, current));
        valueView.setText(formatValue(clamped, suffix));
        valueView.setPadding(dp(12), dp(8), dp(12), dp(8));
        valueView.setBackground(roundRect(colorSurfaceSoft, 999));
        header.addView(valueView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView subtitle = new TextView(this);
        subtitle.setText(subtitleText);
        subtitle.setTextColor(colorSubtext);
        subtitle.setTextSize(13);
        subtitle.setPadding(0, dp(4), 0, 0);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(max - min);
        seekBar.setProgress(clamped - min);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = min + progress;
                valueView.setText(formatValue(value, suffix));
                if (fromUser) {
                    putIntSetting(key, value);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                putIntSetting(key, min + seekBar.getProgress());
            }
        });
        valueView.setOnClickListener(v -> showIntInputDialog(
                titleText,
                min + seekBar.getProgress(),
                min,
                max,
                suffix,
                value -> {
                    valueView.setText(formatValue(value, suffix));
                    seekBar.setProgress(value - min);
                    putIntSetting(key, value);
                }));

        row.addView(header, matchWrap());
        row.addView(subtitle, matchWrap());
        row.addView(seekBar, matchWrapWithTop(8));
        root.addView(row, matchWrap());
    }

    private void addInsetSliderRow(LinearLayout root, String titleText, String subtitleText,
            String key, int defaultValue, int min, int max) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(colorText);
        title.setTextSize(16);
        header.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView valueView = new TextView(this);
        valueView.setTextColor(colorPrimary);
        valueView.setTextSize(14);
        valueView.setPadding(dp(12), dp(8), dp(12), dp(8));
        valueView.setBackground(roundRect(colorSurfaceSoft, 999));
        int current = readIntSetting(key, defaultValue);
        int clamped = Math.max(min, Math.min(max, current));
        valueView.setText(formatInsetValue(clamped));
        header.addView(valueView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView subtitle = new TextView(this);
        subtitle.setText(subtitleText);
        subtitle.setTextColor(colorSubtext);
        subtitle.setTextSize(13);
        subtitle.setPadding(0, dp(4), 0, 0);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(max - min);
        seekBar.setProgress(clamped - min);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = min + progress;
                valueView.setText(formatInsetValue(value));
                if (fromUser) {
                    putIntSetting(key, value);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                putIntSetting(key, min + seekBar.getProgress());
            }
        });
        valueView.setOnClickListener(v -> showIntInputDialog(
                titleText,
                min + seekBar.getProgress(),
                min,
                max,
                "",
                value -> {
                    valueView.setText(formatInsetValue(value));
                    seekBar.setProgress(value - min);
                    putIntSetting(key, value);
                }));

        row.addView(header, matchWrap());
        row.addView(subtitle, matchWrap());
        row.addView(seekBar, matchWrapWithTop(8));
        root.addView(row, matchWrap());
    }

    private void addSliderRowWithFallback(LinearLayout root, String titleText, String subtitleText, String key,
            int defaultValue, String fallbackKey, int fallbackDefaultValue, int min, int max, String suffix) {
        int initialValue = getIntValueWithFallback(key, defaultValue, fallbackKey, fallbackDefaultValue);
        addSliderRow(root, titleText, subtitleText, key, initialValue, min, max, suffix);
    }

    private void addTextSettingRow(LinearLayout root, String titleText, String subtitleText,
            String key, String defaultValue, String emptyLabel) {
        addTextSettingRow(root, titleText, subtitleText, key, defaultValue, emptyLabel, null, false);
    }

    private void addTextSettingRow(LinearLayout root, String titleText, String subtitleText,
            String key, String defaultValue, String emptyLabel, String inputHint, boolean plainTextInput) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(colorText);
        title.setTextSize(16);
        textColumn.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText(subtitleText);
        subtitle.setTextColor(colorSubtext);
        subtitle.setTextSize(13);
        subtitle.setPadding(0, dp(4), dp(10), 0);
        textColumn.addView(subtitle, matchWrap());

        TextView valueView = new TextView(this);
        valueView.setTextColor(colorPrimary);
        valueView.setTextSize(13);
        valueView.setPadding(dp(12), dp(8), dp(12), dp(8));
        valueView.setBackground(roundRect(colorSurfaceSoft, 999));
        valueView.setMaxWidth(dp(180));
        valueView.setSingleLine(false);
        updateTextSettingLabel(valueView, readStringSetting(key, defaultValue), emptyLabel);
        valueView.setOnClickListener(v -> showTextInputDialog(
                titleText,
                readStringSetting(key, defaultValue),
                subtitleText,
                inputHint,
                plainTextInput,
                value -> {
                    putStringSetting(key, value);
                    updateTextSettingLabel(valueView, value, emptyLabel);
                }));

        row.addView(textColumn, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(valueView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(row, matchWrap());
    }

    private void addChoiceRow(LinearLayout root, String titleText, String subtitleText,
            String key, int defaultValue, int[] values, String[] labels) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(colorText);
        title.setTextSize(16);
        textColumn.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText(subtitleText);
        subtitle.setTextColor(colorSubtext);
        subtitle.setTextSize(13);
        subtitle.setPadding(0, dp(4), dp(10), 0);
        textColumn.addView(subtitle, matchWrap());

        TextView valueView = new TextView(this);
        valueView.setTextColor(colorPrimary);
        valueView.setTextSize(13);
        valueView.setPadding(dp(12), dp(8), dp(12), dp(8));
        valueView.setBackground(roundRect(colorSurfaceSoft, 999));
        int currentValue = readIntSetting(key, defaultValue);
        valueView.setText(resolveChoiceLabel(currentValue, values, labels));
        valueView.setOnClickListener(v -> showChoiceMenu(v, key, defaultValue, values, labels, valueView));

        row.addView(textColumn, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(valueView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(row, matchWrap());
    }

    private void addActionButtonRow(LinearLayout root, String titleText, String subtitleText,
            String buttonText, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(colorText);
        title.setTextSize(16);
        textColumn.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText(subtitleText);
        subtitle.setTextColor(colorSubtext);
        subtitle.setTextSize(13);
        subtitle.setPadding(0, dp(4), dp(10), 0);
        textColumn.addView(subtitle, matchWrap());

        TextView button = filledButton(buttonText, colorPrimary, Color.WHITE);
        button.setOnClickListener(v -> action.run());

        row.addView(textColumn, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(button, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(row, matchWrap());
    }

    private void addDivider(LinearLayout root) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        lp.topMargin = dp(14);
        lp.bottomMargin = dp(14);
        root.addView(buildDividerView(), lp);
    }

    private View buildDividerView() {
        View divider = new View(this);
        divider.setBackgroundColor(colorStroke);
        return divider;
    }

    private void addProfileSectionHeader(LinearLayout root, String titleText, String subtitleText) {
        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(colorPrimary);
        title.setTextSize(15);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText(subtitleText);
        subtitle.setTextColor(colorSubtext);
        subtitle.setTextSize(12);
        subtitle.setPadding(0, dp(4), 0, 0);
        root.addView(subtitle, matchWrapWithTop(2));
    }

    private void showTopMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, MENU_IMPORT, 0, "\u5bfc\u5165\u914d\u7f6e");
        popup.getMenu().add(0, MENU_EXPORT, 1, "\u5bfc\u51fa\u914d\u7f6e");
        popup.getMenu().add(0, MENU_RESET, 2, "\u6062\u590d\u9ed8\u8ba4");
        popup.getMenu().add(0, MENU_RESTART, 3, "\u91cd\u542f SystemUI");
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == MENU_IMPORT) {
                startImportConfig();
                return true;
            }
            if (id == MENU_EXPORT) {
                startExportConfig();
                return true;
            }
            if (id == MENU_RESET) {
                resetAllSettings();
                return true;
            }
            if (id == MENU_RESTART) {
                restartSystemUi();
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void showChoiceMenu(View anchor, String key, int defaultValue,
            int[] values, String[] labels, TextView valueView) {
        PopupMenu popup = new PopupMenu(this, anchor);
        int currentValue = readIntSetting(key, defaultValue);
        for (int i = 0; i < values.length && i < labels.length; i++) {
            popup.getMenu().add(0, values[i], i, labels[i]);
        }
        popup.setOnMenuItemClickListener(item -> {
            int selectedValue = item.getItemId();
            putIntSetting(key, selectedValue);
            valueView.setText(resolveChoiceLabel(selectedValue, values, labels));
            return true;
        });
        popup.show();
        valueView.setText(resolveChoiceLabel(currentValue, values, labels));
    }

    private int readIntSetting(String key, int defaultValue) {
        return SettingsStore.readInt(prefs, key, defaultValue);
    }

    private String resolveChoiceLabel(int value, int[] values, String[] labels) {
        for (int i = 0; i < values.length && i < labels.length; i++) {
            if (values[i] == value) {
                return labels[i];
            }
        }
        return labels.length > 0 ? labels[0] : "";
    }

    private String readStringSetting(String key, String defaultValue) {
        return SettingsStore.readString(prefs, key, defaultValue);
    }

    private int getIntValueWithFallback(String key, int defaultValue, String fallbackKey, int fallbackDefaultValue) {
        if (prefs.contains(key)) {
            return SettingsStore.readInt(prefs, key, defaultValue);
        }
        return SettingsStore.readInt(prefs, fallbackKey, fallbackDefaultValue);
    }

    private void putBooleanSetting(String key, boolean value) {
        prefs.edit().putBoolean(key, value).apply();
        SettingsStore.notifyChanged(this);
        invalidatePreview();
    }

    private void putIntSetting(String key, int value) {
        prefs.edit().putInt(key, value).apply();
        SettingsStore.notifyChanged(this);
        invalidatePreview();
    }

    private void putStringSetting(String key, String value) {
        prefs.edit().putString(key, value == null ? "" : value).apply();
        SettingsStore.notifyChanged(this);
        invalidatePreview();
    }

    private void testLaunchMBackIntent() {
        String raw = readStringSetting(
                SettingsStore.KEY_MBACK_LONG_TOUCH_INTENT_URI,
                SettingsStore.DEFAULT_MBACK_LONG_TOUCH_INTENT_URI);
        if (TextUtils.isEmpty(raw) || TextUtils.isEmpty(raw.trim())) {
            showToast("请先填写目标 URL 或 Intent URI");
            return;
        }
        try {
            Intent intent;
            String trimmed = raw.trim();
            if (trimmed.startsWith("intent:") || trimmed.contains("#Intent;")) {
                intent = Intent.parseUri(trimmed, Intent.URI_INTENT_SCHEME);
            } else {
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(trimmed));
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            showToast("测试启动已发送");
        } catch (Throwable t) {
            showToast("测试启动失败：" + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()));
        }
    }

    private void invalidatePreview() {
        if (previewView != null) {
            previewView.setBatteryStyle(readIntSetting(
                    SettingsStore.KEY_BATTERY_ICON_STYLE,
                    SettingsStore.DEFAULT_BATTERY_ICON_STYLE));
            previewView.setBatteryTextFont(readIntSetting(
                    SettingsStore.KEY_BATTERY_TEXT_FONT,
                    SettingsStore.DEFAULT_BATTERY_TEXT_FONT));
            previewView.setIconScalePercent(readIntSetting(
                    SettingsStore.KEY_STATUS_BAR_ICON_SCALE_PERCENT,
                    SettingsStore.DEFAULT_STATUS_BAR_ICON_SCALE_PERCENT));
            previewView.setBatteryInnerTextScalePercent(readIntSetting(
                    SettingsStore.KEY_BATTERY_INNER_TEXT_SCALE_PERCENT,
                    SettingsStore.DEFAULT_BATTERY_INNER_TEXT_SCALE_PERCENT));
            previewView.setBatteryLevelTextEnabled(SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_BATTERY_LEVEL_TEXT_ENABLED,
                    SettingsStore.DEFAULT_BATTERY_LEVEL_TEXT_ENABLED));
            previewView.setBatteryHollowEnabled(SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_BATTERY_HOLLOW_ENABLED,
                    SettingsStore.DEFAULT_BATTERY_HOLLOW_ENABLED));
            previewView.setBatteryHollowFillFollowsLevel(SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_BATTERY_HOLLOW_FILL_FOLLOWS_LEVEL,
                    SettingsStore.DEFAULT_BATTERY_HOLLOW_FILL_FOLLOWS_LEVEL));
            previewView.invalidate();
        }
    }

    private void resetAllSettings() {
        prefs.edit().clear().apply();
        SettingsStore.notifyChanged(this);
        invalidatePreview();
        showToast("\u5df2\u6062\u590d\u9ed8\u8ba4\u914d\u7f6e");
        recreate();
    }

    private String formatValue(int value, String suffix) {
        return suffix == null || suffix.length() == 0 ? Integer.toString(value) : value + suffix;
    }

    private String formatInsetValue(int value) {
        return value < 0 ? "系统默认" : value + "dp";
    }

    private void startExportConfig() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "flyme_status_bar_sizer_config.json");
        startActivityForResult(intent, REQUEST_EXPORT_CONFIG);
    }

    private void startImportConfig() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent, REQUEST_IMPORT_CONFIG);
    }

    private void exportConfig(Uri uri) {
        try (OutputStream output = getContentResolver().openOutputStream(uri)) {
            if (output == null) {
                showToast("\u65e0\u6cd5\u6253\u5f00\u5bfc\u51fa\u6587\u4ef6");
                return;
            }
            JSONObject root = new JSONObject();
            JSONObject settings = new JSONObject();
            root.put("schema", "flyme_status_bar_sizer");
            root.put("version", 2);
            for (String key : SettingsStore.BOOLEAN_KEYS) {
                if (SettingsStore.includeInBackup(key)) {
                    settings.put(key, SettingsStore.readBoolean(
                            prefs, key, SettingsStore.defaultBoolean(key)));
                }
            }
            for (String key : SettingsStore.INT_KEYS) {
                if (SettingsStore.includeInBackup(key)) {
                    settings.put(key, SettingsStore.readInt(
                            prefs, key, SettingsStore.defaultInt(key)));
                }
            }
            for (String key : SettingsStore.STRING_KEYS) {
                if (SettingsStore.includeInBackup(key)) {
                    settings.put(key, SettingsStore.readString(
                            prefs, key, SettingsStore.defaultString(key)));
                }
            }
            root.put("settings", settings);
            output.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
            showToast("\u914d\u7f6e\u5df2\u5bfc\u51fa");
        } catch (Throwable t) {
            showToast("\u5bfc\u51fa\u5931\u8d25\uff1a" + t.getMessage());
        }
    }

    private void importConfig(Uri uri) {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) {
                showToast("\u65e0\u6cd5\u6253\u5f00\u5bfc\u5165\u6587\u4ef6");
                return;
            }
            JSONObject root = new JSONObject(readText(input));
            JSONObject settings = root.optJSONObject("settings");
            if (settings == null) {
                showToast("\u5bfc\u5165\u5931\u8d25\uff1a\u914d\u7f6e\u6587\u4ef6\u683c\u5f0f\u4e0d\u6b63\u786e");
                return;
            }
            if (!"flyme_status_bar_sizer".equals(root.optString("schema"))
                    || root.optInt("version", 0) != 2) {
                showToast("\u5bfc\u5165\u5931\u8d25\uff1a\u53ea\u652f\u6301\u65b0\u7248 v2 \u914d\u7f6e\u6587\u4ef6");
                return;
            }
            SharedPreferences.Editor editor = prefs.edit().clear();
            for (String key : SettingsStore.BOOLEAN_KEYS) {
                if (!SettingsStore.includeInBackup(key)) {
                    continue;
                }
                editor.putBoolean(key, settings.optBoolean(key, SettingsStore.defaultBoolean(key)));
            }
            for (String key : SettingsStore.INT_KEYS) {
                if (!SettingsStore.includeInBackup(key)) {
                    continue;
                }
                editor.putInt(key, settings.optInt(key, SettingsStore.defaultInt(key)));
            }
            for (String key : SettingsStore.STRING_KEYS) {
                if (!SettingsStore.includeInBackup(key)) {
                    continue;
                }
                editor.putString(key, settings.optString(key, SettingsStore.defaultString(key)));
            }
            editor.apply();
            SettingsStore.notifyChanged(this);
            invalidatePreview();
            showToast("\u914d\u7f6e\u5df2\u5bfc\u5165");
            recreate();
        } catch (Throwable t) {
            showToast("\u5bfc\u5165\u5931\u8d25\uff1a" + t.getMessage());
        }
    }

    private String readText(InputStream input) throws java.io.IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }

    private void showIntInputDialog(String titleText, int currentValue, int min, int max, String suffix,
            IntValueConsumer consumer) {
        EditText input = new EditText(this);
        input.setText(String.valueOf(currentValue));
        input.setSelection(input.getText().length());
        input.setInputType(min < 0
                ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED
                : InputType.TYPE_CLASS_NUMBER);
        input.setHint(min + " ~ " + max);
        int padding = dp(20);
        input.setPadding(padding, padding, padding, padding);

        new AlertDialog.Builder(this)
                .setTitle(titleText)
                .setMessage("\u8f93\u5165\u8303\u56f4 " + min + " ~ " + max + (suffix == null ? "" : suffix))
                .setView(input)
                .setNegativeButton("\u53d6\u6d88", null)
                .setPositiveButton("\u786e\u5b9a", (dialog, which) -> {
                    String text = input.getText() == null ? "" : input.getText().toString().trim();
                    if (text.length() == 0) {
                        showToast("\u8bf7\u8f93\u5165\u6570\u503c");
                        return;
                    }
                    try {
                        int value = Integer.parseInt(text);
                        int clamped = Math.max(min, Math.min(max, value));
                        consumer.accept(clamped);
                    } catch (NumberFormatException ignored) {
                        showToast("\u8f93\u5165\u683c\u5f0f\u4e0d\u6b63\u786e");
                    }
                })
                .show();
    }

    private void showTextInputDialog(String titleText, String currentValue, String message,
            String inputHint, boolean plainTextInput, TextValueConsumer consumer) {
        EditText input = new EditText(this);
        input.setText(currentValue == null ? "" : currentValue);
        input.setSelection(input.getText().length());
        if (plainTextInput) {
            input.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            input.setHint(inputHint == null ? "" : inputHint);
        } else {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
            input.setHint(inputHint == null ? "https://example.com or intent://..." : inputHint);
        }
        input.setMinLines(2);
        input.setMaxLines(6);
        int padding = dp(20);
        input.setPadding(padding, padding, padding, padding);

        new AlertDialog.Builder(this)
                .setTitle(titleText)
                .setMessage(message)
                .setView(input)
                .setNeutralButton("清空", (dialog, which) -> consumer.accept(""))
                .setNegativeButton("\u53d6\u6d88", null)
                .setPositiveButton("\u786e\u5b9a", (dialog, which) ->
                        consumer.accept(input.getText() == null ? "" : input.getText().toString().trim()))
                .show();
    }

    private void updateTextSettingLabel(TextView valueView, String value, String emptyLabel) {
        if (TextUtils.isEmpty(value)) {
            valueView.setText(emptyLabel);
            return;
        }
        valueView.setText(value);
    }

    private View buildClockExpressionButtonPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        clockExpressionButtons.clear();
        for (String[] rowTokens : CLOCK_EXPRESSION_TOKEN_ROWS) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, panel.getChildCount() == 0 ? 0 : dp(10), 0, 0);
            for (int i = 0; i < rowTokens.length; i++) {
                String token = rowTokens[i];
                TextView button = buildClockExpressionTokenButton(token);
                clockExpressionButtons.put(token, button);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                if (i > 0) {
                    lp.leftMargin = dp(8);
                }
                row.addView(button, lp);
            }
            panel.addView(row, matchWrap());
        }
        return panel;
    }

    private TextView buildClockExpressionTokenButton(String token) {
        TextView button = new TextView(this);
        button.setText(getClockExpressionTokenLabel(token));
        button.setTextColor(colorPrimary);
        button.setTextSize(13);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(10), dp(12), dp(10), dp(12));
        button.setBackground(outlinedRect(colorSurface, colorStroke, 1, 18));
        button.setTag(token);
        button.setOnClickListener(v -> {
            Object tag = v.getTag();
            if (!(tag instanceof String)) {
                return;
            }
            String currentToken = (String) tag;
            if (clockExpressionDraftTokens.contains(currentToken)) {
                removeClockExpressionToken(currentToken);
            } else {
                clockExpressionDraftTokens.add(currentToken);
                renderClockExpressionEditor();
            }
        });
        return button;
    }

    private void loadClockExpressionDraft() {
        clockExpressionDraftTokens.clear();
        String raw = readStringSetting(
                SettingsStore.KEY_CLOCK_CUSTOM_FORMAT,
                SettingsStore.DEFAULT_CLOCK_CUSTOM_FORMAT);
        if (!TextUtils.isEmpty(raw)) {
            Matcher matcher = CLOCK_EXPRESSION_TOKEN_PATTERN.matcher(raw);
            while (matcher.find()) {
                String token = matcher.group(1);
                if (isValidClockExpressionToken(token)) {
                    clockExpressionDraftTokens.add(token);
                }
            }
        }
        syncClockExpressionButtons();
    }

    private boolean isValidClockExpressionToken(String token) {
        return "HH".equals(token)
                || "H".equals(token)
                || "hh".equals(token)
                || "h".equals(token)
                || "mm".equals(token)
                || "ss".equals(token)
                || "week".equals(token)
                || "week_short".equals(token)
                || "week_1".equals(token)
                || "ampm".equals(token)
                || "period".equals(token)
                || "branch".equals(token)
                || "branch_alias".equals(token);
    }

    private void renderClockExpressionEditor() {
        syncClockExpressionButtons();
        updateClockExpressionPreview();
        if (clockExpressionOrderContainer == null) {
            return;
        }
        clockExpressionOrderContainer.removeAllViews();
        if (clockExpressionDraftTokens.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("当前为空，状态栏时间会回退到系统原始时间显示。");
            empty.setTextColor(colorSubtext);
            empty.setTextSize(13);
            empty.setPadding(0, dp(4), 0, 0);
            clockExpressionOrderContainer.addView(empty, matchWrap());
            return;
        }
        for (int i = 0; i < clockExpressionDraftTokens.size(); i++) {
            String token = clockExpressionDraftTokens.get(i);
            clockExpressionOrderContainer.addView(buildClockExpressionOrderRow(token), matchWrap());
            if (i < clockExpressionDraftTokens.size() - 1) {
                View divider = new View(this);
                divider.setBackgroundColor(colorStroke);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
                lp.topMargin = dp(8);
                lp.bottomMargin = dp(8);
                clockExpressionOrderContainer.addView(divider, lp);
            }
        }
    }

    private View buildClockExpressionOrderRow(String token) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setBackground(outlinedRect(colorSurface, colorStroke, 1, 20));
        row.setTag(token);

        TextView drag = new TextView(this);
        drag.setText("≡");
        drag.setTextColor(colorPrimary);
        drag.setTextSize(18);
        row.addView(drag, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.setPadding(dp(12), 0, 0, 0);

        TextView title = new TextView(this);
        title.setText(getClockExpressionTokenLabel(token));
        title.setTextColor(colorText);
        title.setTextSize(15);
        textColumn.addView(title, matchWrap());

        TextView value = new TextView(this);
        value.setText("{" + token + "}");
        value.setTextColor(colorSubtext);
        value.setTextSize(12);
        value.setPadding(0, dp(4), 0, 0);
        textColumn.addView(value, matchWrap());
        row.addView(textColumn, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView remove = chip("点击移除", colorSurfaceStrong, colorPrimary);
        row.addView(remove, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        row.setOnClickListener(v -> {
            Object tag = v.getTag();
            if (tag instanceof String) {
                removeClockExpressionToken((String) tag);
            }
        });
        row.setOnLongClickListener(v -> {
            String currentToken = v.getTag() instanceof String ? (String) v.getTag() : "";
            ClipData data = ClipData.newPlainText("clock_expression_token", currentToken);
            View.DragShadowBuilder shadow = new View.DragShadowBuilder(v);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                v.startDragAndDrop(data, shadow, v, 0);
            } else {
                v.startDrag(data, shadow, v, 0);
            }
            v.setAlpha(0.55f);
            return true;
        });
        row.setOnDragListener((v, event) -> handleClockExpressionRowDrag(v, event));
        return row;
    }

    private boolean handleClockExpressionRowDrag(View target, DragEvent event) {
        if (!(target.getTag() instanceof String)) {
            return false;
        }
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return event.getLocalState() instanceof View
                        && ((View) event.getLocalState()).getTag() instanceof String;
            case DragEvent.ACTION_DRAG_ENTERED:
                target.setBackground(outlinedRect(colorSurfaceStrong, colorFeatureStroke, 1, 20));
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
                target.setBackground(outlinedRect(colorSurface, colorStroke, 1, 20));
                return true;
            case DragEvent.ACTION_DROP:
                target.setBackground(outlinedRect(colorSurface, colorStroke, 1, 20));
                Object localState = event.getLocalState();
                if (!(localState instanceof View) || !((((View) localState).getTag()) instanceof String)) {
                    return false;
                }
                moveClockExpressionToken((String) ((View) localState).getTag(), (String) target.getTag());
                syncClockExpressionEditorRows();
                updateClockExpressionPreview();
                syncClockExpressionButtons();
                return true;
            case DragEvent.ACTION_DRAG_ENDED:
                target.setBackground(outlinedRect(colorSurface, colorStroke, 1, 20));
                Object draggedView = event.getLocalState();
                if (draggedView instanceof View) {
                    ((View) draggedView).setAlpha(1f);
                }
                syncClockExpressionEditorRows();
                updateClockExpressionPreview();
                syncClockExpressionButtons();
                return true;
            default:
                return true;
        }
    }

    private void moveClockExpressionToken(String fromToken, String toToken) {
        if (TextUtils.isEmpty(fromToken) || TextUtils.isEmpty(toToken) || fromToken.equals(toToken)) {
            return;
        }
        int fromIndex = clockExpressionDraftTokens.indexOf(fromToken);
        int toIndex = clockExpressionDraftTokens.indexOf(toToken);
        if (fromIndex < 0 || toIndex < 0) {
            return;
        }
        clockExpressionDraftTokens.remove(fromIndex);
        if (fromIndex < toIndex) {
            toIndex--;
        }
        clockExpressionDraftTokens.add(toIndex, fromToken);
    }

    private void syncClockExpressionEditorRows() {
        if (clockExpressionOrderContainer == null) {
            return;
        }
        int orderIndex = 0;
        for (int i = 0; i < clockExpressionOrderContainer.getChildCount(); i++) {
            View child = clockExpressionOrderContainer.getChildAt(i);
            if (!(child instanceof LinearLayout) || orderIndex >= clockExpressionDraftTokens.size()) {
                continue;
            }
            LinearLayout row = (LinearLayout) child;
            String token = clockExpressionDraftTokens.get(orderIndex++);
            row.setTag(token);
            row.setAlpha(1f);
            row.setBackground(outlinedRect(colorSurface, colorStroke, 1, 20));
            View titleView = null;
            View valueView = null;
            if (row.getChildCount() > 1 && row.getChildAt(1) instanceof LinearLayout) {
                LinearLayout textColumn = (LinearLayout) row.getChildAt(1);
                titleView = textColumn.getChildCount() > 0 ? textColumn.getChildAt(0) : null;
                valueView = textColumn.getChildCount() > 1 ? textColumn.getChildAt(1) : null;
            }
            if (titleView instanceof TextView) {
                ((TextView) titleView).setText(getClockExpressionTokenLabel(token));
            }
            if (valueView instanceof TextView) {
                ((TextView) valueView).setText("{" + token + "}");
            }
        }
    }

    private void removeClockExpressionToken(String token) {
        int index = clockExpressionDraftTokens.indexOf(token);
        if (index < 0) {
            return;
        }
        clockExpressionDraftTokens.remove(index);
        renderClockExpressionEditor();
    }

    private void updateClockExpressionPreview() {
        if (clockExpressionPreviewView == null) {
            return;
        }
        String format = buildClockExpressionFormat(clockExpressionDraftTokens);
        clockExpressionPreviewView.setText(TextUtils.isEmpty(format) ? "未设置" : format);
    }

    private void syncClockExpressionButtons() {
        for (String token : clockExpressionButtons.keySet()) {
            TextView button = clockExpressionButtons.get(token);
            if (button == null) {
                continue;
            }
            boolean selected = clockExpressionDraftTokens.contains(token);
            button.setTextColor(selected ? Color.WHITE : colorPrimary);
            button.setBackground(outlinedRect(
                    selected ? colorPrimary : colorSurface,
                    selected ? colorPrimary : colorStroke,
                    1,
                    18));
        }
    }

    private String getClockExpressionTokenLabel(String token) {
        if ("HH".equals(token)) {
            return "24小时";
        }
        if ("H".equals(token)) {
            return "24小时单数";
        }
        if ("hh".equals(token)) {
            return "12小时";
        }
        if ("h".equals(token)) {
            return "12小时单数";
        }
        if ("mm".equals(token)) {
            return "分钟";
        }
        if ("ss".equals(token)) {
            return "秒";
        }
        if ("week".equals(token)) {
            return "星期";
        }
        if ("week_short".equals(token)) {
            return "周";
        }
        if ("week_1".equals(token)) {
            return "周简写";
        }
        if ("ampm".equals(token)) {
            return "AM/PM";
        }
        if ("period".equals(token)) {
            return "时段词";
        }
        if ("branch".equals(token)) {
            return "地支";
        }
        if ("branch_alias".equals(token)) {
            return "传统别称";
        }
        return token;
    }

    private void applyClockExpressionDraft() {
        String format = buildClockExpressionFormat(clockExpressionDraftTokens);
        putStringSetting(SettingsStore.KEY_CLOCK_CUSTOM_FORMAT, format);
        showToast(TextUtils.isEmpty(format) ? "已清空自定义时间表达式" : "自定义时间表达式已应用");
    }

    private void clearClockExpressionDraft() {
        clockExpressionDraftTokens.clear();
        renderClockExpressionEditor();
        showToast("当前表达式已清空，点应用后才会写入设置");
    }

    private String buildClockExpressionFormat(ArrayList<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (!isValidClockExpressionToken(token)) {
                continue;
            }
            String previous = findPreviousClockToken(tokens, i);
            if (builder.length() > 0) {
                builder.append(resolveClockExpressionSeparator(previous, token));
            }
            builder.append('{').append(token).append('}');
        }
        return builder.toString();
    }

    private String findPreviousClockToken(ArrayList<String> tokens, int index) {
        for (int i = index - 1; i >= 0; i--) {
            String token = tokens.get(i);
            if (isValidClockExpressionToken(token)) {
                return token;
            }
        }
        return null;
    }

    private String resolveClockExpressionSeparator(String previous, String current) {
        if (TextUtils.isEmpty(previous) || TextUtils.isEmpty(current)) {
            return "";
        }
        if (isClockTimeUnitToken(previous) && isClockTimeUnitToken(current)) {
            if (("HH".equals(previous) || "H".equals(previous) || "hh".equals(previous) || "h".equals(previous))
                    && "mm".equals(current)) {
                return ":";
            }
            if ("mm".equals(previous) && "ss".equals(current)) {
                return ":";
            }
        }
        return " ";
    }

    private boolean isClockTimeUnitToken(String token) {
        return "HH".equals(token) || "H".equals(token)
                || "hh".equals(token) || "h".equals(token)
                || "mm".equals(token) || "ss".equals(token);
    }

    private void loadImeToolbarDraftOrder() {
        imeToolbarDraftOrder.clear();
        String raw = readStringSetting(
                SettingsStore.KEY_IME_TOOLBAR_ORDER,
                SettingsStore.DEFAULT_IME_TOOLBAR_ORDER);
        if (!TextUtils.isEmpty(raw)) {
            String[] parts = raw.split(",");
            for (String part : parts) {
                String action = part == null ? "" : part.trim();
                if (isValidImeToolbarAction(action) && !imeToolbarDraftOrder.contains(action)) {
                    imeToolbarDraftOrder.add(action);
                }
            }
        }
        addMissingImeToolbarActions(imeToolbarDraftOrder);
    }

    private void addMissingImeToolbarActions(ArrayList<String> target) {
        String[] defaults = new String[]{"paste", "delete", "select_all", "copy", "switch_ime"};
        for (String action : defaults) {
            if (!target.contains(action)) {
                target.add(action);
            }
        }
    }

    private boolean isValidImeToolbarAction(String action) {
        return "paste".equals(action)
                || "delete".equals(action)
                || "select_all".equals(action)
                || "copy".equals(action)
                || "switch_ime".equals(action);
    }

    private void renderImeToolbarOrderEditor() {
        if (imeToolbarOrderContainer == null) {
            return;
        }
        imeToolbarOrderContainer.removeAllViews();
        for (int i = 0; i < imeToolbarDraftOrder.size(); i++) {
            String action = imeToolbarDraftOrder.get(i);
            imeToolbarOrderContainer.addView(buildImeToolbarOrderRow(action), matchWrap());
            if (i < imeToolbarDraftOrder.size() - 1) {
                View divider = new View(this);
                divider.setBackgroundColor(colorStroke);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
                lp.topMargin = dp(8);
                lp.bottomMargin = dp(8);
                imeToolbarOrderContainer.addView(divider, lp);
            }
        }
    }

    private View buildImeToolbarOrderRow(String action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setBackground(outlinedRect(colorSurface, colorStroke, 1, 20));
        row.setTag(action);

        TextView drag = new TextView(this);
        drag.setText("≡");
        drag.setTextColor(colorPrimary);
        drag.setTextSize(18);
        row.addView(drag, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText(getImeToolbarActionLabel(action));
        title.setTextColor(colorText);
        title.setTextSize(15);
        title.setPadding(dp(12), 0, 0, 0);
        row.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView summary = chip("长按拖动", colorSurfaceStrong, colorPrimary);
        row.addView(summary, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        row.setOnLongClickListener(v -> {
            String currentAction = v.getTag() instanceof String ? (String) v.getTag() : "";
            ClipData data = ClipData.newPlainText("ime_toolbar_action", currentAction);
            View.DragShadowBuilder shadow = new View.DragShadowBuilder(v);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                v.startDragAndDrop(data, shadow, v, 0);
            } else {
                v.startDrag(data, shadow, v, 0);
            }
            v.setAlpha(0.55f);
            return true;
        });
        row.setOnDragListener((v, event) -> handleImeToolbarRowDrag(v, event));
        return row;
    }

    private boolean handleImeToolbarRowDrag(View target, DragEvent event) {
        if (!(target.getTag() instanceof String)) {
            return false;
        }
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return event.getLocalState() instanceof View
                        && ((View) event.getLocalState()).getTag() instanceof String;
            case DragEvent.ACTION_DRAG_ENTERED:
                target.setBackground(outlinedRect(colorSurfaceStrong, colorFeatureStroke, 1, 20));
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
                target.setBackground(outlinedRect(colorSurface, colorStroke, 1, 20));
                return true;
            case DragEvent.ACTION_DROP:
                target.setBackground(outlinedRect(colorSurface, colorStroke, 1, 20));
                Object localState = event.getLocalState();
                if (!(localState instanceof View) || !((((View) localState).getTag()) instanceof String)) {
                    return false;
                }
                moveImeToolbarAction((String) ((View) localState).getTag(), (String) target.getTag());
                syncImeToolbarOrderEditorRows();
                return true;
            case DragEvent.ACTION_DRAG_ENDED:
                target.setBackground(outlinedRect(colorSurface, colorStroke, 1, 20));
                Object draggedView = event.getLocalState();
                if (draggedView instanceof View) {
                    ((View) draggedView).setAlpha(1f);
                }
                syncImeToolbarOrderEditorRows();
                return true;
            default:
                return true;
        }
    }

    private void moveImeToolbarAction(String fromAction, String toAction) {
        if (TextUtils.isEmpty(fromAction) || TextUtils.isEmpty(toAction) || fromAction.equals(toAction)) {
            return;
        }
        int fromIndex = imeToolbarDraftOrder.indexOf(fromAction);
        int toIndex = imeToolbarDraftOrder.indexOf(toAction);
        if (fromIndex < 0 || toIndex < 0) {
            return;
        }
        imeToolbarDraftOrder.remove(fromIndex);
        if (fromIndex < toIndex) {
            toIndex--;
        }
        imeToolbarDraftOrder.add(toIndex, fromAction);
    }

    private String getImeToolbarActionLabel(String action) {
        if ("paste".equals(action)) {
            return "粘贴";
        }
        if ("delete".equals(action)) {
            return "删除";
        }
        if ("select_all".equals(action)) {
            return "全选";
        }
        if ("copy".equals(action)) {
            return "复制";
        }
        if ("switch_ime".equals(action)) {
            return "切换输入法";
        }
        return action;
    }

    private void applyImeToolbarOrder() {
        if (imeToolbarDraftOrder.size() != 5) {
            showToast("按钮顺序数据不完整");
            return;
        }
        putStringSetting(SettingsStore.KEY_IME_TOOLBAR_ORDER, TextUtils.join(",", imeToolbarDraftOrder));
        showToast("输入法工具栏顺序已应用");
    }

    private void syncImeToolbarOrderEditorRows() {
        if (imeToolbarOrderContainer == null) {
            return;
        }
        int orderIndex = 0;
        for (int i = 0; i < imeToolbarOrderContainer.getChildCount(); i++) {
            View child = imeToolbarOrderContainer.getChildAt(i);
            if (!(child instanceof LinearLayout) || orderIndex >= imeToolbarDraftOrder.size()) {
                continue;
            }
            LinearLayout row = (LinearLayout) child;
            String action = imeToolbarDraftOrder.get(orderIndex++);
            row.setTag(action);
            row.setAlpha(1f);
            row.setBackground(outlinedRect(colorSurface, colorStroke, 1, 20));
            View titleView = row.getChildCount() > 1 ? row.getChildAt(1) : null;
            if (titleView instanceof TextView) {
                ((TextView) titleView).setText(getImeToolbarActionLabel(action));
            }
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void restartSystemUi() {
        restartRootCommands("SystemUI", new String[]{
                "killall " + PACKAGE_SYSTEM_UI,
                "pkill -f " + PACKAGE_SYSTEM_UI,
                "am crash " + PACKAGE_SYSTEM_UI
        });
    }

    private void restartPackageProcess(String packageName, String label) {
        restartRootCommands(label, new String[]{
                "am force-stop " + packageName,
                "pkill -f " + packageName,
                "killall " + packageName
        });
    }

    private void restartRootCommands(String label, String[] commands) {
        showToast("\u6b63\u5728\u91cd\u542f" + label + "...");
        new Thread(() -> {
            boolean success = false;
            String error = null;
            try {
                if (commands != null) {
                    for (String command : commands) {
                        if (command == null || command.trim().length() == 0) {
                            continue;
                        }
                        Process process = new ProcessBuilder("su", "-c", command)
                                .redirectErrorStream(true)
                                .start();
                        String output = readText(process.getInputStream()).trim();
                        int exitCode = process.waitFor();
                        if (exitCode == 0) {
                            success = true;
                            break;
                        }
                        if (output.length() > 0) {
                            error = output;
                        }
                    }
                }
            } catch (Throwable t) {
                error = t.getMessage();
            }
            boolean finalSuccess = success;
            String finalError = error;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (finalSuccess) {
                    showToast(label + "\u5df2\u91cd\u542f");
                } else if (finalError == null || finalError.length() == 0) {
                    showToast("\u91cd\u542f\u5931\u8d25\uff0c\u8bf7\u786e\u8ba4\u5df2\u6388\u4e88 root \u6743\u9650");
                } else {
                    showToast("\u91cd\u542f\u5931\u8d25\uff1a" + finalError);
                }
            });
        }).start();
    }

    private LinearLayout card(int color, int radiusDp) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(roundRect(color, radiusDp));
        return card;
    }

    private LinearLayout card(int color, int strokeColor, int radiusDp) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(outlinedRect(color, strokeColor, 1, radiusDp));
        return card;
    }

    private TextView chip(String text, int backgroundColor, int textColor) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(textColor);
        view.setTextSize(12);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(12), dp(6), dp(12), dp(6));
        view.setBackground(roundRect(backgroundColor, 99));
        return view;
    }

    private TextView filledButton(String text, int backgroundColor, int textColor) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(textColor);
        view.setTextSize(14);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(14), dp(10), dp(14), dp(10));
        view.setBackground(roundRect(backgroundColor, 999));
        return view;
    }

    private GradientDrawable gradientCard() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{colorPrimaryContainer, colorPrimary, colorPrimaryDeep});
        drawable.setCornerRadius(dp(32));
        return drawable;
    }

    private interface TextValueConsumer {
        void accept(String value);
    }

    private void initPalette() {
        colorBackground = resolveMonetColor("system_neutral1_10", FALLBACK_BACKGROUND);
        colorSurface = resolveMonetColor("system_neutral1_0", FALLBACK_SURFACE);
        colorSurfaceSoft = resolveMonetColor("system_neutral2_50", FALLBACK_SURFACE_SOFT);
        colorSurfaceStrong = resolveMonetColor("system_accent1_100", FALLBACK_SURFACE_STRONG);
        colorFeatureSurface = resolveMonetColor("system_accent2_50", FALLBACK_FEATURE_SURFACE);
        colorFeatureStroke = resolveMonetColor("system_accent1_200", FALLBACK_FEATURE_STROKE);
        colorText = resolveMonetColor("system_neutral1_900", FALLBACK_TEXT);
        colorSubtext = resolveMonetColor("system_neutral2_700", FALLBACK_SUBTEXT);
        colorPrimary = resolveMonetColor("system_accent1_600", FALLBACK_PRIMARY);
        colorPrimaryContainer = resolveMonetColor("system_accent1_300", FALLBACK_PRIMARY_CONTAINER);
        colorPrimaryDeep = resolveMonetColor("system_accent1_800", FALLBACK_PRIMARY_DEEP);
        colorStroke = resolveMonetColor("system_neutral2_200", FALLBACK_STROKE);
    }

    private void configureSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            getWindow().setStatusBarColor(colorSurface);
            getWindow().setNavigationBarColor(colorSurface);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = getWindow().getDecorView().getSystemUiVisibility();
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    private int resolveMonetColor(String androidColorName, int fallback) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return fallback;
        }
        try {
            Resources resources = Resources.getSystem();
            int resId = resources.getIdentifier(androidColorName, "color", "android");
            if (resId != 0) {
                return resources.getColor(resId, getTheme());
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private GradientDrawable outlinedRect(int color, int strokeColor, int strokeWidthDp, int radiusDp) {
        GradientDrawable drawable = roundRect(color, radiusDp);
        drawable.setStroke(dp(strokeWidthDp), strokeColor);
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchWrapWithTop(int topDp) {
        LinearLayout.LayoutParams lp = matchWrap();
        lp.topMargin = dp(topDp);
        return lp;
    }

    private LinearLayout.LayoutParams weightedWrap() {
        return new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    private LinearLayout.LayoutParams weightedWrapWithStart(int startDp) {
        LinearLayout.LayoutParams lp = weightedWrap();
        lp.leftMargin = dp(startDp);
        return lp;
    }

    private FrameLayout.LayoutParams matchWrapFrame() {
        return new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
    }

    private FrameLayout.LayoutParams floatingMenuLayoutParams(int topInset) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.topMargin = dp(16) + topInset;
        lp.rightMargin = dp(20);
        return lp;
    }

    private FrameLayout.LayoutParams bottomNavigationLayoutParams() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM;
        lp.leftMargin = dp(20);
        lp.rightMargin = dp(20);
        lp.bottomMargin = dp(20);
        return lp;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int getStatusBarInset() {
        int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resId != 0) {
            try {
                return getResources().getDimensionPixelSize(resId);
            } catch (Resources.NotFoundException ignored) {
            }
        }
        return dp(24);
    }

    private interface IntValueConsumer {
        void accept(int value);
    }

}
