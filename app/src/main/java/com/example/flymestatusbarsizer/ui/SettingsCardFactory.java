package com.example.flymestatusbarsizer.ui;

import com.example.flymestatusbarsizer.MainActivity;
import com.example.flymestatusbarsizer.config.SettingsStore;
import com.example.flymestatusbarsizer.feature.share.ShareTargetsEditor;
import com.example.flymestatusbarsizer.feature.assistant.AssistantBackgroundSettings;
import com.example.flymestatusbarsizer.feature.battery.BatteryTextFontHelper;
import com.example.flymestatusbarsizer.feature.battery.CircleBatteryAnimationEditor;
import com.example.flymestatusbarsizer.feature.battery.CircleBatteryAppearanceEditor;
import com.example.flymestatusbarsizer.feature.launcher.LauncherStackScaleCurveView;
import com.example.flymestatusbarsizer.feature.notification.anip.AnipBundleStore;
import com.example.flymestatusbarsizer.feature.notification.anip.AnipIconLibrary;
import com.example.flymestatusbarsizer.feature.notification.anip.AnipIconLibraryEditor;
import com.example.flymestatusbarsizer.feature.notification.anip.AnipIconMode;
import com.example.flymestatusbarsizer.feature.onehanded.OneStepWorkspaceProtocol;
import com.example.flymestatusbarsizer.feature.signal.SignalAppearancePreviewView;
import com.example.flymestatusbarsizer.feature.wifi.WifiAppearancePreviewView;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

public final class SettingsCardFactory {
    private final MainActivity activity;
    private LinearLayout batteryIconOptions;
    private LinearLayout batterySizingOptions;

    public SettingsCardFactory(MainActivity activity) {
        this.activity = activity;
    }

    public View createIconSizingCard() {
        return buildStatusBarIconScaleCard();
    }

    public View createBatterySettingsCard() {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);

        LinearLayout circleBatteryOptions = new LinearLayout(activity);
        circleBatteryOptions.setOrientation(LinearLayout.VERTICAL);
        circleBatteryOptions.setVisibility(SettingsStore.readBoolean(
                activity.prefs(),
                SettingsStore.KEY_CAMERA_CIRCLE_BATTERY_ENABLED,
                SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_ENABLED)
                ? View.VISIBLE : View.GONE);
        activity.addSwitchRow(content, "环形电池",
                "开启后在摄像头挖孔周围显示环形电池。",
                SettingsStore.KEY_CAMERA_CIRCLE_BATTERY_ENABLED,
                SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_ENABLED,
                (buttonView, isChecked) -> {
                    circleBatteryOptions.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                    updateBatteryOptionsVisibility();
                });
        activity.addSwitchRow(circleBatteryOptions, "隐藏原电池图标",
                "同时隐藏模块绘制和系统原生电池图标，并释放原图标占用的位置。",
                SettingsStore.KEY_CAMERA_CIRCLE_BATTERY_HIDE_ICON_ENABLED,
                SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_HIDE_ICON_ENABLED,
                (buttonView, isChecked) -> updateBatteryOptionsVisibility());
        activity.addDivider(circleBatteryOptions);
        LinearLayout circleAppearance = new LinearLayout(activity);
        circleAppearance.setOrientation(LinearLayout.VERTICAL);
        activity.addSliderRow(circleAppearance, "环形大小",
                "调整环形电池大小，放大后可避开摄像头周围的黑边。",
                SettingsStore.KEY_CAMERA_CIRCLE_BATTERY_RADIUS_PERCENT,
                SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_RADIUS_PERCENT,
                80, 200, "%");
        activity.addSliderRow(circleAppearance, "环形线条粗细",
                "只调整圆环线条粗细，不改变环形大小。",
                SettingsStore.KEY_CAMERA_CIRCLE_BATTERY_STROKE_PERCENT,
                SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_STROKE_PERCENT,
                50, 300, "%");
        new CircleBatteryAppearanceEditor(activity).addTo(circleAppearance);
        circleBatteryOptions.addView(activity.buildSectionCard("环形电池外观",
                "调整环形电池的大小、粗细、透明度和状态颜色。状态优先级为：充电、低电量、省电、正常。", circleAppearance),
                PageViewUtils.matchWrap());
        LinearLayout dynamics = new LinearLayout(activity);
        dynamics.setOrientation(LinearLayout.VERTICAL);
        new CircleBatteryAnimationEditor(activity).addTo(dynamics);
        circleBatteryOptions.addView(activity.buildSectionCard("动画效果",
                "普通状态使用柔和流光，充电时增强彗星效果。省电、熄屏或隐藏时暂停动画。", dynamics),
                PageViewUtils.matchWrap());
        LinearLayout.LayoutParams circleBatteryOptionsLp = activity.matchWrapWithTop(10);
        circleBatteryOptionsLp.leftMargin = activity.dp(12);
        content.addView(circleBatteryOptions, circleBatteryOptionsLp);
        batteryIconOptions = new LinearLayout(activity);
        batteryIconOptions.setOrientation(LinearLayout.VERTICAL);
        activity.addDivider(batteryIconOptions);
        activity.addSwitchRow(batteryIconOptions, "接管电池图标绘制",
                "关闭后恢复系统电池图标，模块不再控制电池图标的样式和大小。",
                SettingsStore.KEY_BATTERY_CODE_DRAW_ENABLED,
                SettingsStore.DEFAULT_BATTERY_CODE_DRAW_ENABLED);
        activity.addDivider(batteryIconOptions);
        activity.addChoiceRow(batteryIconOptions, "电池样式",
                "可选择类 iOS、类 One UI 和 iOS 旧版三种样式。",
                SettingsStore.KEY_BATTERY_ICON_STYLE,
                SettingsStore.DEFAULT_BATTERY_ICON_STYLE,
                new int[]{SettingsStore.BATTERY_STYLE_IOS, SettingsStore.BATTERY_STYLE_ONEUI,
                        SettingsStore.BATTERY_STYLE_FLYME_CAPSULE},
                new String[]{"类 iOS", "类 One UI", "iOS 旧版"});
        activity.addDivider(batteryIconOptions);
        LinearLayout hollowSection = new LinearLayout(activity);
        hollowSection.setOrientation(LinearLayout.VERTICAL);
        hollowSection.setVisibility(SettingsStore.readBoolean(
                activity.prefs(),
                SettingsStore.KEY_BATTERY_LEVEL_TEXT_ENABLED,
                SettingsStore.DEFAULT_BATTERY_LEVEL_TEXT_ENABLED) ? View.VISIBLE : View.GONE);
        LinearLayout hollowOptions = buildBatteryHollowOptions();
        hollowOptions.setVisibility(SettingsStore.readBoolean(
                activity.prefs(),
                SettingsStore.KEY_BATTERY_HOLLOW_ENABLED,
                SettingsStore.DEFAULT_BATTERY_HOLLOW_ENABLED) ? View.VISIBLE : View.GONE);
        activity.addSwitchRow(batteryIconOptions, "显示电池电量数字",
                "关闭后只显示电池图形，不在图形内部显示剩余电量。",
                SettingsStore.KEY_BATTERY_LEVEL_TEXT_ENABLED,
                SettingsStore.DEFAULT_BATTERY_LEVEL_TEXT_ENABLED,
                (buttonView, isChecked) -> hollowSection.setVisibility(isChecked ? View.VISIBLE : View.GONE));
        activity.addSwitchRow(hollowSection, "镂空显示电量",
                "开启后用透明镂空方式显示电量数字。",
                SettingsStore.KEY_BATTERY_HOLLOW_ENABLED,
                SettingsStore.DEFAULT_BATTERY_HOLLOW_ENABLED,
                (buttonView, isChecked) -> hollowOptions.setVisibility(isChecked ? View.VISIBLE : View.GONE));
        LinearLayout.LayoutParams hollowOptionsLp = activity.matchWrapWithTop(10);
        hollowOptionsLp.leftMargin = activity.dp(12);
        hollowSection.addView(hollowOptions, hollowOptionsLp);
        LinearLayout.LayoutParams hollowSectionLp = activity.matchWrapWithTop(10);
        hollowSectionLp.leftMargin = activity.dp(12);
        batteryIconOptions.addView(hollowSection, hollowSectionLp);
        activity.addDivider(batteryIconOptions);
        int[] batteryTextFontOptions = BatteryTextFontHelper.getAvailableFontOptions(activity);
        activity.addChoiceRow(batteryIconOptions, "电量数字字体",
                "可选择系统字体，也可以使用模块内置的 MiSansLatinVFNumber。",
                SettingsStore.KEY_BATTERY_TEXT_FONT,
                SettingsStore.DEFAULT_BATTERY_TEXT_FONT,
                batteryTextFontOptions,
                BatteryTextFontHelper.getFontLabels(batteryTextFontOptions));
        activity.addDivider(batteryIconOptions);
        LinearLayout batteryAppearance = new LinearLayout(activity);
        batteryAppearance.setOrientation(LinearLayout.VERTICAL);
        activity.addApplySliderRow(batteryAppearance, "电池主体宽度",
                "默认值为 100%。类 One UI 和带电池帽的两种样式均生效。",
                SettingsStore.KEY_BATTERY_BODY_WIDTH_PERCENT,
                SettingsStore.DEFAULT_BATTERY_BODY_WIDTH_PERCENT, 50, 150, "%");
        activity.addApplySliderRow(batteryAppearance, "电池主体高度",
                "默认值为 100%。只调整图形高度，不改变状态栏图标占用大小。",
                SettingsStore.KEY_BATTERY_BODY_HEIGHT_PERCENT,
                SettingsStore.DEFAULT_BATTERY_BODY_HEIGHT_PERCENT, 50, 150, "%");
        activity.addApplySliderRow(batteryAppearance, "电池图形圆角",
                "默认值保持当前样式。0% 为直角，200% 为最大圆角。",
                SettingsStore.KEY_BATTERY_CORNER_RADIUS_PERCENT,
                SettingsStore.DEFAULT_BATTERY_CORNER_RADIUS_PERCENT, 0, 200, "%");
        activity.addApplySliderRow(batteryAppearance, "电池帽宽度",
                "默认值为 100%，仅类 iOS 和 iOS 旧版样式生效。",
                SettingsStore.KEY_BATTERY_CAP_WIDTH_PERCENT,
                SettingsStore.DEFAULT_BATTERY_CAP_WIDTH_PERCENT, 50, 150, "%");
        batteryIconOptions.addView(activity.buildSectionCard("电池图标外观",
                "调整普通电池图标的宽度、高度、圆角和电池帽。", batteryAppearance),
                PageViewUtils.matchWrap());
        content.addView(batteryIconOptions, PageViewUtils.matchWrap());
        updateBatteryOptionsVisibility();
        return activity.buildSectionCard(
                "电池图标",
                "在这里选择电池图标样式，并调整电量数字和镂空效果。",
                content);
    }

    private void updateBatteryOptionsVisibility() {
        boolean hideIcon = SettingsStore.readBoolean(activity.prefs(),
                SettingsStore.KEY_CAMERA_CIRCLE_BATTERY_ENABLED,
                SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_ENABLED)
                && SettingsStore.readBoolean(activity.prefs(),
                SettingsStore.KEY_CAMERA_CIRCLE_BATTERY_HIDE_ICON_ENABLED,
                SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_HIDE_ICON_ENABLED);
        int visibility = hideIcon ? View.GONE : View.VISIBLE;
        if (batteryIconOptions != null) {
            batteryIconOptions.setVisibility(visibility);
        }
        if (batterySizingOptions != null) {
            batterySizingOptions.setVisibility(visibility);
        }
    }

    public View createNotificationSettingsCard() {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);

        LinearLayout appIconOptions = buildNotificationAppIconOptions();
        appIconOptions.setVisibility(SettingsStore.readBoolean(
                activity.prefs(),
                SettingsStore.KEY_NOTIFICATION_APP_ICON_ENABLED,
                SettingsStore.DEFAULT_NOTIFICATION_APP_ICON_ENABLED) ? View.VISIBLE : View.GONE);
        activity.addSwitchRow(content, "使用应用通知图标",
                "开启后使用第三方应用自己的图标，不再使用 Flyme 的统一通知图标。",
                SettingsStore.KEY_NOTIFICATION_APP_ICON_ENABLED,
                SettingsStore.DEFAULT_NOTIFICATION_APP_ICON_ENABLED,
                (buttonView, isChecked) -> appIconOptions.setVisibility(isChecked ? View.VISIBLE : View.GONE));
        LinearLayout.LayoutParams optionsLp = activity.matchWrapWithTop(10);
        optionsLp.leftMargin = activity.dp(12);
        content.addView(appIconOptions, optionsLp);
        return activity.buildSectionCard(
                "通知图标",
                "只调整第三方应用通知图标的来源、大小和边距。",
                content);
    }

    public View createSignalSettingsCard() {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);

        activity.addSwitchRow(content, "接管移动网络图标绘制",
                "关闭后恢复系统的移动网络和 Wi-Fi 图标，模块不再替换图标位置和大小。",
                SettingsStore.KEY_SIGNAL_CODE_DRAW_ENABLED,
                SettingsStore.DEFAULT_SIGNAL_CODE_DRAW_ENABLED);
        activity.addDivider(content);
        LinearLayout signalAppearance = new LinearLayout(activity);
        signalAppearance.setOrientation(LinearLayout.VERTICAL);
        signalAppearance.addView(new SignalAppearancePreviewView(activity, activity.prefs(),
                activity.textColor()), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, activity.dp(112)));
        activity.addDefaultableSliderRow(signalAppearance, "第一根信号柱高度",
                "相对最高柱高度百分比；0% 隐藏此柱。",
                SettingsStore.KEY_SIGNAL_BAR1_HEIGHT_PERCENT,
                SettingsStore.DEFAULT_SIGNAL_BAR1_HEIGHT_PERCENT, 0, 100, "%");
        activity.addDefaultableSliderRow(signalAppearance, "第二根信号柱高度",
                "相对最高柱高度百分比；0% 隐藏此柱。",
                SettingsStore.KEY_SIGNAL_BAR2_HEIGHT_PERCENT,
                SettingsStore.DEFAULT_SIGNAL_BAR2_HEIGHT_PERCENT, 0, 100, "%");
        activity.addDefaultableSliderRow(signalAppearance, "第三根信号柱高度",
                "相对最高柱高度百分比；0% 隐藏此柱。",
                SettingsStore.KEY_SIGNAL_BAR3_HEIGHT_PERCENT,
                SettingsStore.DEFAULT_SIGNAL_BAR3_HEIGHT_PERCENT, 0, 100, "%");
        activity.addDefaultableSliderRow(signalAppearance, "信号柱圆角",
                "0% 为直角，100% 为全圆角；圆角会适应每根柱子的宽高。",
                SettingsStore.KEY_SIGNAL_BAR_CORNER_RADIUS_PERCENT,
                SettingsStore.DEFAULT_SIGNAL_BAR_CORNER_RADIUS_PERCENT, 0, 100, "%");
        activity.addDefaultableSliderRow(signalAppearance, "双卡圆点圆角",
                "调整双卡合一下方的副卡信号点；0% 为方形，100% 为圆形。",
                SettingsStore.KEY_SIGNAL_DOT_CORNER_RADIUS_PERCENT,
                SettingsStore.DEFAULT_SIGNAL_DOT_CORNER_RADIUS_PERCENT, 0, 100, "%");
        content.addView(activity.buildSectionCard("移动网络图标外观",
                "拖动会自动保存，也可以点击数值精确输入或恢复默认。预览为放大显示，双卡示例为主卡 3 格、副卡 2 格。",
                signalAppearance),
                PageViewUtils.matchWrap());
        activity.addDivider(content);
        LinearLayout badgeTextOptions = new LinearLayout(activity);
        badgeTextOptions.setOrientation(LinearLayout.VERTICAL);
        boolean badgeEnabled = SettingsStore.readBoolean(
                activity.prefs(),
                SettingsStore.KEY_SIGNAL_MOBILE_TYPE_BADGE_ENABLED,
                SettingsStore.DEFAULT_SIGNAL_MOBILE_TYPE_BADGE_ENABLED);
        badgeTextOptions.setVisibility(badgeEnabled ? View.VISIBLE : View.GONE);
        activity.addSwitchRow(content, "显示网络类型文字",
                "关闭后只显示移动网络图标，不显示右侧的网络类型文字。",
                SettingsStore.KEY_SIGNAL_MOBILE_TYPE_BADGE_ENABLED,
                SettingsStore.DEFAULT_SIGNAL_MOBILE_TYPE_BADGE_ENABLED,
                (buttonView, isChecked) -> badgeTextOptions.setVisibility(
                        isChecked ? View.VISIBLE : View.GONE));
        activity.addDivider(badgeTextOptions);
        activity.addTextSettingRow(badgeTextOptions, "5G 标签文字",
                "检测到 5G 时显示；留空表示隐藏且不占用位置。",
                SettingsStore.KEY_SIGNAL_MOBILE_TYPE_BADGE_5G_TEXT,
                SettingsStore.DEFAULT_SIGNAL_MOBILE_TYPE_BADGE_5G_TEXT,
                "不显示",
                "例如 5G",
                true);
        activity.addDivider(badgeTextOptions);
        activity.addTextSettingRow(badgeTextOptions, "5GA 标签文字",
                "检测到 5GA 时显示；留空表示隐藏且不占用位置。",
                SettingsStore.KEY_SIGNAL_MOBILE_TYPE_BADGE_5GA_TEXT,
                SettingsStore.DEFAULT_SIGNAL_MOBILE_TYPE_BADGE_5GA_TEXT,
                "不显示",
                "例如 5GA",
                true);
        activity.addDivider(badgeTextOptions);
        activity.addTextSettingRow(badgeTextOptions, "其他网络标签文字",
                "检测到其他移动网络类型时显示；留空表示隐藏且不占用位置。",
                SettingsStore.KEY_SIGNAL_MOBILE_TYPE_BADGE_NON_5G_TEXT,
                SettingsStore.DEFAULT_SIGNAL_MOBILE_TYPE_BADGE_NON_5G_TEXT,
                "不显示",
                "例如 4G",
                true);
        LinearLayout.LayoutParams badgeTextOptionsLp = PageViewUtils.matchWrap();
        badgeTextOptionsLp.leftMargin = activity.dp(12);
        content.addView(badgeTextOptions, badgeTextOptionsLp);
        activity.addDivider(content);
        LinearLayout wifiAppearance = new LinearLayout(activity);
        wifiAppearance.setOrientation(LinearLayout.VERTICAL);
        wifiAppearance.addView(new WifiAppearancePreviewView(activity, activity.prefs(),
                activity.textColor()), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, activity.dp(112)));
        activity.addDefaultableSliderRow(wifiAppearance, "弧带圆角",
                "两条弧带端部及底部扇形顶部两角的圆润程度，按弧带线宽计算。0% 为直角。",
                SettingsStore.KEY_WIFI_BAND_CORNER_PERCENT,
                SettingsStore.DEFAULT_WIFI_BAND_CORNER_PERCENT, 0, 45, "%");
        activity.addDefaultableSliderRow(wifiAppearance, "底部圆角",
                "底部尖角的圆润程度，按弧带线宽计算。0% 为尖角。",
                SettingsStore.KEY_WIFI_TIP_CORNER_PERCENT,
                SettingsStore.DEFAULT_WIFI_TIP_CORNER_PERCENT, 0, 30, "%");
        activity.addDefaultableSliderRow(wifiAppearance, "层间距",
                "层间空隙与弧带线宽的比例。保持图标总高度，间距增大时弧带会相应变细。",
                SettingsStore.KEY_WIFI_BAND_GAP_PERCENT,
                SettingsStore.DEFAULT_WIFI_BAND_GAP_PERCENT, 40, 120, "%");
        View wifiAppearanceCard = activity.buildSectionCard("Wi-Fi 图标外观",
                "拖动会自动保存，也可以点击数值精确输入。双 Wi-Fi 会同步调整，预览为放大显示。",
                wifiAppearance);
        wifiAppearanceCard.setVisibility(SettingsStore.readBoolean(activity.prefs(),
                SettingsStore.KEY_WIFI_CODE_DRAW_ENABLED,
                SettingsStore.DEFAULT_WIFI_CODE_DRAW_ENABLED) ? View.VISIBLE : View.GONE);
        activity.addSwitchRow(content, "接管 Wi-Fi 图标绘制",
                "在“接管移动网络图标绘制”开启时，单独控制是否接管 Wi-Fi 图标。",
                SettingsStore.KEY_WIFI_CODE_DRAW_ENABLED,
                SettingsStore.DEFAULT_WIFI_CODE_DRAW_ENABLED,
                (buttonView, isChecked) -> wifiAppearanceCard.setVisibility(
                        isChecked ? View.VISIBLE : View.GONE));
        content.addView(wifiAppearanceCard, activity.matchWrapWithTop(10));
        activity.addDivider(content);
        activity.addSwitchRow(content, "调换 Wi-Fi 和移动网络位置",
                "让 Wi-Fi 图标显示在电池和移动网络图标之间。",
                SettingsStore.KEY_SIGNAL_WIFI_SWAP_ENABLED,
                SettingsStore.DEFAULT_SIGNAL_WIFI_SWAP_ENABLED);
        activity.addDivider(content);
        return activity.buildSectionCard(
                "移动网络与 Wi-Fi",
                "可分别控制移动网络图标和 Wi-Fi 图标是否由模块绘制。",
                content);
    }

    public View createConnectionRateSettingsCard() {
        return activity.buildSectionCard(
                "实时网速显示",
                "保留系统采样，只调整网速文字的显示阈值和确认次数。",
                buildConnectionRateThresholdPage());
    }

    public View createTimeExpressionSettingsCard() {
        return activity.buildSectionCard(
                "时间显示格式",
                "通过按钮组合时间格式，并在当前页面排序和应用。",
                activity.clockExpressionEditor().buildPage());
    }

    public View createTimeInteractionSettingsCard() {
        return activity.buildSectionCard(
                "时钟点击交互",
                "设置点击左上角时钟后显示的详细信息窗口，默认 8 秒后自动收起。",
                buildTimeInteractionPage());
    }

    public View createTimeTypographySettingsCard() {
        return activity.buildSectionCard(
                "状态栏文字",
                "统一调整时间、日期、网速、锁屏运营商和系统电量数字的粗细。",
                buildTimeTypographyPage());
    }

    public View createMBackActionSettingsCard() {
        return activity.buildSectionCard(
                "MBack 长按动作",
                "只接管 MBack 长按操作，保留单击和其他系统操作。",
                buildMBackActionPage());
    }

    public View createWindowModeSideGestureSettingsCard() {
        return activity.buildSectionCard(
                "小窗选择面板",
                "调整 Flyme 原生小窗选择面板及其侧边触发手势。",
                buildWindowModeSideGesturePage());
    }

    public View createSideGestureSettingsCard() {
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        activity.addSwitchRow(page, "启用侧边手势增强",
                "扩展系统侧滑手势；锁屏时不会触发。需在 LSPosed 启用 SystemUI 范围并重启 SystemUI。",
                SettingsStore.KEY_ASSISTANT_GESTURE_ENABLED, SettingsStore.DEFAULT_ASSISTANT_GESTURE_ENABLED);
        activity.addChoiceRow(page, "返回动画样式",
                "选择侧边返回时显示的动画；悬浮箭头会跟随斜滑方向并轻微区分长短距离。",
                SettingsStore.KEY_SIDE_BACK_ANIMATION_STYLE,
                SettingsStore.DEFAULT_SIDE_BACK_ANIMATION_STYLE,
                new int[]{SettingsStore.SIDE_BACK_ANIMATION_RIBBON,
                        SettingsStore.SIDE_BACK_ANIMATION_FLOATING_ARROW},
                new String[]{"Ribbon", "悬浮返回箭头"});
        activity.addActionButtonRow(page, "手势动作与触发设置",
                "分别设置左右边缘的动作、触发距离和预览。", "打开",
                () -> activity.openPage(MainActivity.Page.SIDE_GESTURE_SETTINGS));
        return activity.buildSectionCard("侧边手势增强", null, page);
    }

    public View createSideGestureSettingsPage() {
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        LinearLayout assistantOptions = new LinearLayout(activity);
        assistantOptions.setOrientation(LinearLayout.VERTICAL);
        activity.addMultiChoiceRow(assistantOptions, "生效场景",
                "仅控制选择“全局负一屏”的手势在哪些界面生效。",
                SettingsStore.KEY_ASSISTANT_GESTURE_SCENES, SettingsStore.DEFAULT_ASSISTANT_GESTURE_SCENES,
                new int[]{SettingsStore.ASSISTANT_GESTURE_SCENE_NORMAL,
                        SettingsStore.ASSISTANT_GESTURE_SCENE_NOTIFICATION,
                        SettingsStore.ASSISTANT_GESTURE_SCENE_CONTROL_CENTER},
                new String[]{"应用和桌面", "通知栏", "控制中心"}, "未选择触发场景");
        activity.addDivider(assistantOptions);
        AssistantBackgroundSettings.addRows(activity, assistantOptions);
        View appearanceCard = activity.buildSectionCard("全局负一屏",
                "下次打开全局负一屏时生效，不影响桌面负一屏。", assistantOptions, true);
        appearanceCard.setBackground(activity.roundRect(activity.surfaceSoftColor(), 8));
        View workspaceCard = activity.buildSectionCard("多应用工作台",
                "可在主窗口和侧边小窗之间切换多个应用。",
                buildTaskWorkbenchOptions(false), true);
        workspaceCard.setBackground(activity.roundRect(activity.surfaceSoftColor(), 8));

        String[] shortActionKeys = {
                SettingsStore.KEY_SIDE_GESTURE_LEFT_ACTION,
                SettingsStore.KEY_SIDE_GESTURE_LEFT_UP_ACTION,
                SettingsStore.KEY_SIDE_GESTURE_LEFT_DOWN_ACTION,
                SettingsStore.KEY_SIDE_GESTURE_RIGHT_ACTION,
                SettingsStore.KEY_SIDE_GESTURE_RIGHT_UP_ACTION,
                SettingsStore.KEY_SIDE_GESTURE_RIGHT_DOWN_ACTION
        };
        String[] legacyActionKeys = {
                SettingsStore.KEY_SIDE_GESTURE_ACTION,
                SettingsStore.KEY_SIDE_GESTURE_UP_ACTION,
                SettingsStore.KEY_SIDE_GESTURE_DOWN_ACTION,
                SettingsStore.KEY_SIDE_GESTURE_ACTION,
                SettingsStore.KEY_SIDE_GESTURE_UP_ACTION,
                SettingsStore.KEY_SIDE_GESTURE_DOWN_ACTION
        };
        String[] longActionKeys = {
                SettingsStore.KEY_SIDE_GESTURE_LEFT_LONG_ACTION,
                SettingsStore.KEY_SIDE_GESTURE_LEFT_UP_LONG_ACTION,
                SettingsStore.KEY_SIDE_GESTURE_LEFT_DOWN_LONG_ACTION,
                SettingsStore.KEY_SIDE_GESTURE_RIGHT_LONG_ACTION,
                SettingsStore.KEY_SIDE_GESTURE_RIGHT_UP_LONG_ACTION,
                SettingsStore.KEY_SIDE_GESTURE_RIGHT_DOWN_LONG_ACTION
        };
        Runnable updateActionVisibility = () -> {
            boolean splitEnabled = SettingsStore.readBoolean(activity.prefs(),
                    SettingsStore.KEY_ASSISTANT_GESTURE_DISTANCE_SPLIT_ENABLED,
                    SettingsStore.DEFAULT_ASSISTANT_GESTURE_DISTANCE_SPLIT_ENABLED);
            boolean hasAssistant = false;
            boolean hasWorkspace = false;
            for (int i = 0; i < shortActionKeys.length; i++) {
                int action = SettingsStore.readSideGestureAction(activity.prefs(),
                        shortActionKeys[i], legacyActionKeys[i]);
                hasAssistant |= action == SettingsStore.SIDE_GESTURE_ACTION_GLOBAL_ASSISTANT;
                hasWorkspace |= action == SettingsStore.SIDE_GESTURE_ACTION_TASK_SCALE;
                if (splitEnabled) {
                    action = SettingsStore.readSideGestureActionWithFallback(activity.prefs(),
                            longActionKeys[i], shortActionKeys[i], legacyActionKeys[i]);
                    hasAssistant |= action == SettingsStore.SIDE_GESTURE_ACTION_GLOBAL_ASSISTANT;
                    hasWorkspace |= action == SettingsStore.SIDE_GESTURE_ACTION_TASK_SCALE;
                }
            }
            appearanceCard.setVisibility(hasAssistant ? View.VISIBLE : View.GONE);
            workspaceCard.setVisibility(hasWorkspace ? View.VISIBLE : View.GONE);
        };

        LinearLayout gestureOptions = new LinearLayout(activity);
        gestureOptions.setOrientation(LinearLayout.VERTICAL);
        boolean splitEnabled = SettingsStore.readBoolean(activity.prefs(),
                SettingsStore.KEY_ASSISTANT_GESTURE_DISTANCE_SPLIT_ENABLED,
                SettingsStore.DEFAULT_ASSISTANT_GESTURE_DISTANCE_SPLIT_ENABLED);
        LinearLayout triggerOptions = new LinearLayout(activity);
        triggerOptions.setOrientation(LinearLayout.VERTICAL);
        triggerOptions.setVisibility(splitEnabled ? View.GONE : View.VISIBLE);
        activity.addChoiceRow(triggerOptions, "触发时机", "达到最短滑动距离后立即触发，或等手指松开后触发。",
                SettingsStore.KEY_ASSISTANT_GESTURE_TRIGGER_MODE,
                SettingsStore.DEFAULT_ASSISTANT_GESTURE_TRIGGER_MODE,
                new int[]{SettingsStore.ASSISTANT_GESTURE_TRIGGER_AT_DISTANCE,
                        SettingsStore.ASSISTANT_GESTURE_TRIGGER_ON_RELEASE},
                new String[]{"达到距离立即触发", "松手后触发"});
        activity.addSwitchRow(gestureOptions, "区分短距离和长距离动作",
                "开启后等手指松开，再按最终滑动距离选择对应动作。",
                SettingsStore.KEY_ASSISTANT_GESTURE_DISTANCE_SPLIT_ENABLED,
                SettingsStore.DEFAULT_ASSISTANT_GESTURE_DISTANCE_SPLIT_ENABLED,
                (buttonView, isChecked) -> {
                    triggerOptions.setVisibility(isChecked ? View.GONE : View.VISIBLE);
                    updateActionVisibility.run();
                });
        activity.addDivider(gestureOptions);
        activity.addProfileSectionHeader(gestureOptions, "滑动距离",
                "达到最短距离后才会触发；开启分段后，按长距离阈值切换动作。");
        activity.addSwitchRow(gestureOptions, "显示当前功能提示",
                "手势过程中显示当前对应的功能名称。",
                SettingsStore.KEY_ASSISTANT_GESTURE_PREVIEW_ENABLED,
                SettingsStore.DEFAULT_ASSISTANT_GESTURE_PREVIEW_ENABLED);
        activity.addSwitchRow(gestureOptions, "显示触发区域",
                "有效侧边手势开始时，在屏幕上标出各方向区域、触发距离和对应功能。",
                SettingsStore.KEY_ASSISTANT_GESTURE_ZONE_OVERLAY_ENABLED,
                SettingsStore.DEFAULT_ASSISTANT_GESTURE_ZONE_OVERLAY_ENABLED);
        gestureOptions.addView(triggerOptions, activity.matchWrapWithTop(8));
        activity.addActionButtonRow(gestureOptions, "侧边手势设置",
                "打开侧边触发区域设置，可切换左右侧、调整滑动距离和设置动作。",
                "开始编辑", activity::openSideGestureZoneOverlay);
        View gestureCard = activity.buildSectionCard("滑动距离与触发", null, gestureOptions, true);
        gestureCard.setBackground(activity.roundRect(activity.surfaceSoftColor(), 8));

        activity.setSideGestureActionsChangedListener(updateActionVisibility);
        updateActionVisibility.run();
        page.addView(gestureCard, activity.matchWrapWithTop(8));
        page.addView(appearanceCard, activity.matchWrapWithTop(8));
        page.addView(workspaceCard, activity.matchWrapWithTop(8));
        return page;
    }

    public View createOneHandedModeSettingsCard() {
        LinearLayout options = new LinearLayout(activity);
        options.setOrientation(LinearLayout.VERTICAL);
        activity.addSwitchRow(options, "启用底部上滑手势", "关闭后不接管系统单手手势，系统原生单手缩放继续工作。",
                SettingsStore.KEY_ONE_HANDED_MODE_ENABLED, SettingsStore.DEFAULT_ONE_HANDED_MODE_ENABLED);
        activity.addChoiceRow(options, "触发动作", "选择从屏幕底部上滑触发的功能，不影响侧边手势。",
                SettingsStore.KEY_ONE_HANDED_MODE_ACTION, SettingsStore.DEFAULT_ONE_HANDED_MODE_ACTION,
                new int[]{SettingsStore.ONE_HANDED_MODE_ACTION_SYSTEM,
                        SettingsStore.ONE_HANDED_MODE_ACTION_TASK_SCALE},
                new String[]{"系统单手缩放", "多应用工作台"}, value ->
                        updateOneHandedWorkbenchVisibility(options, value));
        updateOneHandedWorkbenchVisibility(options,
                SettingsStore.normalizeOneHandedModeAction(SettingsStore.readInt(activity.prefs(),
                        SettingsStore.KEY_ONE_HANDED_MODE_ACTION,
                        SettingsStore.DEFAULT_ONE_HANDED_MODE_ACTION)));
        return activity.buildSectionCard("底部上滑模式",
                "从屏幕底部上滑触发所选功能；此处设置与侧边手势分别保存。", options, false);
    }

    private void updateOneHandedWorkbenchVisibility(LinearLayout options, int action) {
        View existing = options.findViewWithTag("one_handed_workbench_options");
        if (existing == null) {
            existing = buildTaskWorkbenchOptions(true);
            existing.setTag("one_handed_workbench_options");
            options.addView(existing, activity.matchWrapWithTop(8));
        }
        existing.setVisibility(action == SettingsStore.ONE_HANDED_MODE_ACTION_TASK_SCALE
                ? View.VISIBLE : View.GONE);
    }

    private LinearLayout buildTaskWorkbenchOptions(boolean oneHanded) {
        LinearLayout options = new LinearLayout(activity);
        options.setOrientation(LinearLayout.VERTICAL);

        activity.addProfileSectionHeader(options, "窗口布局", null);
        activity.addSliderRow(options, "顶部工具栏高度", null,
                oneHanded ? SettingsStore.KEY_ONE_HANDED_WORKSPACE_TOOLBAR_HEIGHT_DP
                        : SettingsStore.KEY_WORKSPACE_TOOLBAR_HEIGHT_DP,
                oneHanded ? SettingsStore.DEFAULT_ONE_HANDED_WORKSPACE_TOOLBAR_HEIGHT_DP
                        : SettingsStore.DEFAULT_WORKSPACE_TOOLBAR_HEIGHT_DP,
                SettingsStore.MIN_WORKSPACE_TOOLBAR_HEIGHT_DP,
                SettingsStore.MAX_WORKSPACE_TOOLBAR_HEIGHT_DP, "dp");
        activity.addSwitchRow(options, "进入时展开侧边小窗",
                "进入工作台时显示 3 个侧边小窗；关闭后从收起状态开始。",
                oneHanded ? SettingsStore.KEY_ONE_HANDED_WORKSPACE_DEFAULT_SIDEBAR_ENABLED
                        : SettingsStore.KEY_WORKSPACE_DEFAULT_SIDEBAR_ENABLED,
                oneHanded ? SettingsStore.DEFAULT_ONE_HANDED_WORKSPACE_DEFAULT_SIDEBAR_ENABLED
                        : SettingsStore.DEFAULT_WORKSPACE_DEFAULT_SIDEBAR_ENABLED);
        activity.addActionButtonRow(options, "工作台链接", null, "复制链接", () -> {
            ClipboardManager clipboard = (ClipboardManager)
                    activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) {
                activity.showToast("剪贴板不可用");
                return;
            }
            clipboard.setPrimaryClip(ClipData.newPlainText(
                    "task_workbench_url", OneStepWorkspaceProtocol.URI));
            activity.showToast("工作台链接已复制");
        });
        return options;
    }

    public View createShareTargetsSettingsCard() {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        activity.addActionButtonRow(content, "管理分享面板",
                "可按图片、视频、文本、PDF 和音频分别隐藏、排序，也可单独管理微信好友和朋友圈。"
                        + "首次使用请在 LSPosed 中启用系统分享范围并重启分享进程或手机。",
                "管理", () -> new ShareTargetsEditor(activity).show());
        return activity.buildSectionCard("分享面板",
                "默认跟随系统顺序，可按内容类型隐藏和排序，保存后应用到分享面板。", content);
    }

    public View createCarLinkSettingsCard() {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        TextView restartNote = new TextView(activity);
        restartNote.setText("修改后需重启 CarLink 才能生效");
        restartNote.setTextColor(activity.subtextColor());
        restartNote.setTextSize(12);
        LinearLayout.LayoutParams restartNoteLp = activity.matchWrapWithTop(4);
        restartNoteLp.leftMargin = activity.dp(16);
        restartNoteLp.rightMargin = activity.dp(16);
        content.addView(restartNote, restartNoteLp);
        activity.addSwitchRow(content, "扩展可流转应用",
                "将有桌面入口的第三方应用加入 CarLink 应用管理页。修改后需重启 CarLink。",
                SettingsStore.KEY_CARLINK_EXPAND_APPS_ENABLED,
                SettingsStore.DEFAULT_CARLINK_EXPAND_APPS_ENABLED);
        activity.addDivider(content);
        activity.addActionButtonRow(content, "管理 CarLink 应用",
                "使用 CarLink 原生页面选择、移除和排序车机应用。",
                "打开", activity::openCarLinkAppManagement);
        activity.addDivider(content);
        activity.addSwitchRow(content, "修复网易云首次加载",
                "禁止 CarLink 连接超时时强停网易云，首次加载等待20秒并只重试一次。切换后重启 CarLink 生效。",
                SettingsStore.KEY_CARLINK_NETEASE_COLD_START_FIX_ENABLED,
                SettingsStore.DEFAULT_CARLINK_NETEASE_COLD_START_FIX_ENABLED);
        activity.addDivider(content);
        activity.addSwitchRow(content, "忽略车机昼夜样式",
                "忽略车机发送的昼夜切换指令。修改后需重启 CarLink。",
                SettingsStore.KEY_CARLINK_DAY_NIGHT_ISOLATION_ENABLED,
                SettingsStore.DEFAULT_CARLINK_DAY_NIGHT_ISOLATION_ENABLED);
        activity.addDivider(content);
        activity.addSwitchRow(content, "停止周期性界面重绘",
                "连接状态变化后不再每秒强制刷新主界面。修改后需重启 CarLink。",
                SettingsStore.KEY_CARLINK_PERIODIC_REDRAW_DISABLED,
                SettingsStore.DEFAULT_CARLINK_PERIODIC_REDRAW_DISABLED);
        activity.addDivider(content);
        activity.addSwitchRow(content, "减少触摸调试日志",
                "停止记录高频触摸坐标和界面尺寸。修改后需重启 CarLink。",
                SettingsStore.KEY_CARLINK_TOUCH_LOG_FILTER_ENABLED,
                SettingsStore.DEFAULT_CARLINK_TOUCH_LOG_FILTER_ENABLED);
        activity.addDivider(content);
        activity.addSwitchRow(content, "清理任务监听器",
                "CarLink 投屏界面关闭时清理任务监听器。修改后需重启 CarLink。",
                SettingsStore.KEY_CARLINK_TASK_LISTENER_CLEANUP_ENABLED,
                SettingsStore.DEFAULT_CARLINK_TASK_LISTENER_CLEANUP_ENABLED);
        return activity.buildSectionCard(
                "CarLink",
                "管理应用流转和车机音乐兼容设置。",
                content);
    }

    public View createMBackNavigationSettingsCard() {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(buildMBackImmersivePage(), activity.matchWrap());
        activity.addDivider(content);
        content.addView(buildMBackHeightPage(), activity.matchWrapWithTop(8));
        return activity.buildSectionCard(
                "导航栏背景与抬高",
                "调整导航栏透明背景、白条和 MBack 背景位置。“抬高”也会影响应用对底部区域的判断。",
                content);
    }

    public View createImeToolbarSettingsCard() {
        return activity.buildSectionCard(
                "输入法控制栏",
                "接管输入法控制栏，可调整图标大小、透明度、位置和按钮布局。",
                activity.imeToolbarEditor().buildSettingsContent());
    }

    public View createLauncherRecentsSettingsCard() {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        activity.addSwitchRow(content, "iOS 式堆叠最近任务",
                "仅在竖屏生效，将最近任务卡片显示为重叠堆叠布局；横屏保持系统原生样式。",
                SettingsStore.KEY_LAUNCHER_IOS_STACK_RECENTS_ENABLED,
                SettingsStore.DEFAULT_LAUNCHER_IOS_STACK_RECENTS_ENABLED);
        activity.addDivider(content);
        activity.addSwitchRow(content, "堆叠卡片模糊效果",
                "关闭后保留堆叠布局，但不再模糊边缘卡片的截图和图标。",
                SettingsStore.KEY_LAUNCHER_IOS_STACK_RECENTS_BLUR_ENABLED,
                SettingsStore.DEFAULT_LAUNCHER_IOS_STACK_RECENTS_BLUR_ENABLED);
        activity.addDivider(content);
        activity.addSwitchRow(content, "卡片阴影",
                "给后台卡片添加与下拉菜单相同的系统阴影，关闭后便于对比。",
                SettingsStore.KEY_LAUNCHER_IOS_STACK_RECENTS_SHADOW_ENABLED,
                SettingsStore.DEFAULT_LAUNCHER_IOS_STACK_RECENTS_SHADOW_ENABLED);
        activity.addDivider(content);
        activity.addSwitchRow(content, "显示“清除全部”按钮",
                "仅在启用堆叠最近任务后生效。",
                SettingsStore.KEY_LAUNCHER_IOS_STACK_RECENTS_CLEAR_ALL_BUTTON_ENABLED,
                SettingsStore.DEFAULT_LAUNCHER_IOS_STACK_RECENTS_CLEAR_ALL_BUTTON_ENABLED);
        return activity.buildSectionCard(
                "最近任务卡片",
                "调整系统桌面的最近任务卡片布局，保留原有 Quickstep 手势入口。",
                content);
    }

    public View createSystemAppearanceSettingsCard() {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout content = addSection(root, "通知与最近任务卡片圆角", "分别调整通知卡片和最近任务卡片的圆角。");
        LinearLayout notificationCornerOptions = new LinearLayout(activity);
        notificationCornerOptions.setOrientation(LinearLayout.VERTICAL);
        Switch notificationCornerSwitch = activity.addSwitchRow(content, "自定义通知卡片圆角",
                "开启后使用下方设置的圆角大小；关闭后恢复系统默认。",
                SettingsStore.KEY_NOTIFICATION_CARD_CORNER_RADIUS_ENABLED,
                SettingsStore.DEFAULT_NOTIFICATION_CARD_CORNER_RADIUS_ENABLED,
                (buttonView, isChecked) -> notificationCornerOptions.setAlpha(
                        isChecked ? 1f : 0.45f));
        LinearLayout.LayoutParams cornerOptionsLp = PageViewUtils.matchWrap();
        cornerOptionsLp.leftMargin = activity.dp(12);
        activity.addSliderRow(notificationCornerOptions, "圆角大小",
                "0dp 为直角。Flyme 主通知卡片默认约为 14dp。",
                SettingsStore.KEY_NOTIFICATION_CARD_CORNER_RADIUS_DP,
                SettingsStore.DEFAULT_NOTIFICATION_CARD_CORNER_RADIUS_DP,
                0, 40, "dp");
        notificationCornerOptions.setAlpha(notificationCornerSwitch.isChecked() ? 1f : 0.45f);
        content.addView(notificationCornerOptions, cornerOptionsLp);
        activity.addDivider(content);
        LinearLayout recentsCornerOptions = new LinearLayout(activity);
        recentsCornerOptions.setOrientation(LinearLayout.VERTICAL);
        Switch recentsCornerSwitch = activity.addSwitchRow(content, "自定义最近任务卡片圆角",
                "调整最近任务中的应用预览卡片圆角；关闭后恢复系统默认。",
                SettingsStore.KEY_LAUNCHER_RECENTS_CARD_CORNER_RADIUS_ENABLED,
                SettingsStore.DEFAULT_LAUNCHER_RECENTS_CARD_CORNER_RADIUS_ENABLED,
                (buttonView, isChecked) -> recentsCornerOptions.setAlpha(
                        isChecked ? 1f : 0.45f));
        LinearLayout.LayoutParams recentsCornerOptionsLp = PageViewUtils.matchWrap();
        recentsCornerOptionsLp.leftMargin = activity.dp(12);
        activity.addSliderRow(recentsCornerOptions, "圆角大小",
                "0dp 为直角。多数 Flyme 机型默认约为 24dp。",
                SettingsStore.KEY_LAUNCHER_RECENTS_CARD_CORNER_RADIUS_DP,
                SettingsStore.DEFAULT_LAUNCHER_RECENTS_CARD_CORNER_RADIUS_DP,
                0, 40, "dp");
        recentsCornerOptions.setAlpha(recentsCornerSwitch.isChecked() ? 1f : 0.45f);
        content.addView(recentsCornerOptions, recentsCornerOptionsLp);
        content = addSection(root, "通知背景", "调整系统通知卡片的背景效果和颜色。");
        LinearLayout blurOnlyOptions = new LinearLayout(activity);
        Switch[] textColorSwitchHolder = new Switch[1];
        Switch blurOnlySwitch = activity.addSwitchRow(content, "使用模糊通知背景",
                "开启后移除通知原背景，只保留系统模糊层。",
                SettingsStore.KEY_NOTIFICATION_SYSTEM_BLUR_ONLY_ENABLED,
                SettingsStore.DEFAULT_NOTIFICATION_SYSTEM_BLUR_ONLY_ENABLED,
                (buttonView, isChecked) -> {
                    if (textColorSwitchHolder[0] != null) {
                        textColorSwitchHolder[0].setEnabled(isChecked);
                    }
                    blurOnlyOptions.setAlpha(isChecked ? 1f : 0.45f);
                });
        blurOnlyOptions.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams blurOnlyOptionsLp = PageViewUtils.matchWrap();
        blurOnlyOptionsLp.leftMargin = activity.dp(12);
        activity.addChoiceRow(blurOnlyOptions, "通知背景颜色模式",
                "选择跟随系统、日间或夜间模式显示通知背景颜色。",
                SettingsStore.KEY_NOTIFICATION_SYSTEM_BLUR_CARRIER_COLOR_MODE,
                SettingsStore.DEFAULT_NOTIFICATION_SYSTEM_BLUR_CARRIER_COLOR_MODE,
                new int[]{
                        SettingsStore.NOTIFICATION_SYSTEM_BLUR_CARRIER_COLOR_FOLLOW_SYSTEM,
                        SettingsStore.NOTIFICATION_SYSTEM_BLUR_CARRIER_COLOR_LIGHT,
                        SettingsStore.NOTIFICATION_SYSTEM_BLUR_CARRIER_COLOR_DARK
                },
                new String[]{"跟随系统", "日间模式", "夜间模式"});
        activity.addTextSettingRow(blurOnlyOptions, "日间背景颜色",
                "设置日间通知背景颜色，格式为 #AARRGGBB。",
                SettingsStore.KEY_NOTIFICATION_SYSTEM_BLUR_LIGHT_COLOR,
                SettingsStore.DEFAULT_NOTIFICATION_SYSTEM_BLUR_LIGHT_COLOR,
                SettingsStore.DEFAULT_NOTIFICATION_SYSTEM_BLUR_LIGHT_COLOR,
                SettingsStore.DEFAULT_NOTIFICATION_SYSTEM_BLUR_LIGHT_COLOR,
                true,
                "恢复默认",
                SettingsStore.DEFAULT_NOTIFICATION_SYSTEM_BLUR_LIGHT_COLOR);
        activity.addTextSettingRow(blurOnlyOptions, "夜间背景颜色",
                "设置夜间通知背景颜色，格式为 #AARRGGBB。",
                SettingsStore.KEY_NOTIFICATION_SYSTEM_BLUR_DARK_COLOR,
                SettingsStore.DEFAULT_NOTIFICATION_SYSTEM_BLUR_DARK_COLOR,
                SettingsStore.DEFAULT_NOTIFICATION_SYSTEM_BLUR_DARK_COLOR,
                SettingsStore.DEFAULT_NOTIFICATION_SYSTEM_BLUR_DARK_COLOR,
                true,
                "恢复默认",
                SettingsStore.DEFAULT_NOTIFICATION_SYSTEM_BLUR_DARK_COLOR);
        Switch textColorSwitch = activity.addSwitchRow(blurOnlyOptions, "通知文字跟随状态栏",
                "通知文字使用当前状态栏图标颜色，仅在模糊通知背景开启时生效。",
                SettingsStore.KEY_NOTIFICATION_TEXT_FOLLOW_STATUS_BAR_ENABLED,
                SettingsStore.DEFAULT_NOTIFICATION_TEXT_FOLLOW_STATUS_BAR_ENABLED);
        textColorSwitchHolder[0] = textColorSwitch;
        textColorSwitch.setEnabled(blurOnlySwitch.isChecked());
        blurOnlyOptions.setAlpha(blurOnlySwitch.isChecked() ? 1f : 0.45f);
        content.addView(blurOnlyOptions, blurOnlyOptionsLp);
        content = addSection(root, "桌面分页切换", "在底部分页指示器区域左右拖动，快速切换桌面页面。");
        activity.addSwitchRow(content, "滑动分页指示器切换页面",
                "支持一次拖动跨越多页；显示搜索入口时仍可滑动，点击仍打开搜索。修改后需重启系统桌面。",
                SettingsStore.KEY_LAUNCHER_PAGE_INDICATOR_SWIPE_ENABLED,
                SettingsStore.DEFAULT_LAUNCHER_PAGE_INDICATOR_SWIPE_ENABLED);
        content = addSection(root, "桌面 Aicy 入口", "在桌面分页指示器位置显示 Aicy 入口，并可自定义入口文字和点击后打开的内容。");
        activity.addSwitchRow(content, "显示 Aicy 入口",
                "忽略桌面原开关和 Aicy 助手是否安装。",
                SettingsStore.KEY_LAUNCHER_AICY_ENTRY_ENABLED,
                SettingsStore.DEFAULT_LAUNCHER_AICY_ENTRY_ENABLED);
        activity.addTextSettingRow(content, "入口文字",
                "设置入口显示文字；留空时只显示图标。",
                SettingsStore.KEY_LAUNCHER_AICY_ENTRY_TEXT,
                SettingsStore.DEFAULT_LAUNCHER_AICY_ENTRY_TEXT,
                "仅显示图标",
                "Aicy",
                true);
        activity.addTextSettingRow(content, "点击后打开",
                "填写应用包名、URL 或 Intent URI；留空时打开原生 Aicy 助手。",
                SettingsStore.KEY_LAUNCHER_AICY_ENTRY_TARGET,
                SettingsStore.DEFAULT_LAUNCHER_AICY_ENTRY_TARGET,
                "原生 Aicy",
                "com.example.app 或 intent://...",
                true);
        activity.addActionButtonRow(content, "测试入口",
                "测试当前填写的打开目标。",
                "立即测试", activity::testLaunchLauncherAicyTarget);
        content = addSection(root, "桌面文件夹", "修改 Flyme 桌面文件夹图标的圆角背景颜色。");
        activity.addTextSettingRow(content, "文件夹背景颜色",
                "填写 #AARRGGBB 颜色值。留空表示跟随系统；纯透明为 #00000000。",
                SettingsStore.KEY_LAUNCHER_FOLDER_BG_COLOR,
                SettingsStore.DEFAULT_LAUNCHER_FOLDER_BG_COLOR,
                "跟随系统",
                "#73FFFFFF",
                true);
        return root;
    }

    public View createAdvancedToolsCard() {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        activity.addActionButtonRow(content, "状态栏位置微调",
                "进入独立页面，微调时钟、电池、移动网络、Wi-Fi 和输入法控制栏的位置。",
                "打开", activity::showPositionTuningPage);
        activity.addDivider(content);
        activity.addActionButtonRow(content, "环形电池位置",
                "调整摄像头环形电池相对挖孔中心的位置。",
                "打开", activity::showCameraCirclePositionPage);
        activity.addDivider(content);
        activity.addActionButtonRow(content, "最近任务布局参数",
                "打开独立页面，调整最近任务卡片的布局、动画、手势、视觉和性能。",
                "打开", activity::showLauncherStackParamsPage);
        activity.addDivider(content);
        activity.addActionButtonRow(content, "移动网络状态模拟",
                "打开独立页面，模拟 SIM 卡数量、默认上网卡、网络类型和信号等级。",
                "打开", activity::showTelephonyDebugPage);
        return activity.buildSectionCard(
                "高级工具",
                "用于进一步调整布局或模拟移动网络状态。",
                content);
    }

    public View createConfigManagementCard() {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        activity.addActionButtonRow(content, "导入配置",
                "从 JSON 文件恢复模块配置，会覆盖当前设置。",
                "导入", activity::startImportConfig);
        activity.addDivider(content);
        activity.addActionButtonRow(content, "导出配置",
                "将当前可备份设置导出为 JSON，用于备份或迁移到其他设备。",
                "导出", activity::startExportConfig);
        activity.addDivider(content);
        activity.addActionButtonRow(content, "恢复全部默认设置",
                "清除当前模块设置，并恢复全部默认值。",
                "恢复默认", activity::confirmResetAllSettings);
        return activity.buildSectionCard(
                "配置备份与恢复",
                "导入、导出或恢复模块配置。",
                content);
    }

    public View createPerformanceDebugCard() {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        activity.addSwitchRow(content, "多应用工作台日志",
                "控制多应用工作台初始化和模块错误日志输出。",
                SettingsStore.KEY_WORKSPACE_FLOW_LOGGING_ENABLED,
                SettingsStore.DEFAULT_WORKSPACE_FLOW_LOGGING_ENABLED);
        activity.addDivider(content);
        activity.addSwitchRow(content, "Wi-Fi 性能日志",
                "开启后输出 Wi-Fi 更新过程的耗时日志，便于分析刷新开销。",
                SettingsStore.KEY_WIFI_PERF_LOGGING_ENABLED,
                SettingsStore.DEFAULT_WIFI_PERF_LOGGING_ENABLED);
        activity.addDivider(content);
        activity.addSwitchRow(content, "最近任务性能日志",
                "开启后输出堆叠最近任务交互的帧耗时、卡顿率和慢调用。",
                SettingsStore.KEY_LAUNCHER_RECENTS_PERF_LOGGING_ENABLED,
                SettingsStore.DEFAULT_LAUNCHER_RECENTS_PERF_LOGGING_ENABLED);
        activity.addDivider(content);
        activity.addSwitchRow(content, "最近任务交互日志",
                "开启后输出触摸、分页、删除和动画节点，不输出逐帧日志。",
                SettingsStore.KEY_LAUNCHER_RECENTS_FLOW_LOGGING_ENABLED,
                SettingsStore.DEFAULT_LAUNCHER_RECENTS_FLOW_LOGGING_ENABLED);
        return activity.buildSectionCard(
                "性能日志",
                "管理工作台、Wi-Fi 和最近任务的调试日志与性能数据输出。",
                content);
    }

    public View createLauncherStackParamsSettingsCard() {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout content = addSection(root, "卡片布局", "左侧和右侧均按用户面对屏幕时的方向理解。");
        addStackParam(content, "右侧卡片露出比例", "数值越大，右侧卡片露出越多。",
                SettingsStore.KEY_LAUNCHER_STACK_RIGHT_VISIBLE_PERCENT, 20, 120, "%");
        addStackParam(content, "左侧卡片移动比例", "数值越大，左侧卡片移动距离越大。",
                SettingsStore.KEY_LAUNCHER_STACK_LEFT_MOVE_PERCENT, 0, 200, "%");
        addStackParam(content, "左侧卡片静止内缩比例", "负数会让左侧卡片继续缩进屏幕外。",
                SettingsStore.KEY_LAUNCHER_STACK_LEFT_REST_INSET_PERCENT, -100, 60, "%");
        addStackParam(content, "最小缩放", "边缘卡片的最小缩放比例。",
                SettingsStore.KEY_LAUNCHER_STACK_MIN_SCALE_PERCENT, 60, 110, "%");
        content = addSection(root, "卡片缩放曲线", "拖动两个控制点，调整卡片从最小到最大时的缩放变化。");
        content.addView(
                new LauncherStackScaleCurveView(activity),
                activity.matchWrapWithTop(8));
        addStackParam(content, "最大堆叠层数", "参与深度计算的最大层数。",
                SettingsStore.KEY_LAUNCHER_STACK_MAX_LAYERS, 1, 10, "");

        content = addSection(root, "进入与回弹动画", "调整从应用或桌面进入最近任务，以及松手后的回弹动画。");
        activity.addSwitchRow(content, "当前应用卡片居中",
                "开启后从应用进入最近任务时，让当前应用卡片保持居中；关闭后保持下一张卡片居中。",
                SettingsStore.KEY_LAUNCHER_STACK_CURRENT_APP_CENTERED,
                SettingsStore.DEFAULT_LAUNCHER_STACK_CURRENT_APP_CENTERED);
        activity.addDivider(content);
        addStackParam(content, "入场抬升幅度", "最近任务入场时卡片向上抬升的幅度。",
                SettingsStore.KEY_LAUNCHER_STACK_ENTRY_LIFT_PERCENT, 0, 50, "%");
        addStackParam(content, "入场初始展开程度", "入场起始阶段卡片的展开程度。",
                SettingsStore.KEY_LAUNCHER_STACK_ENTRY_INITIAL_SPREAD_PERCENT, 0, 250, "%");
        addStackParam(content, "回弹初始展开程度", "手势松开后回弹动画的起始展开程度。",
                SettingsStore.KEY_LAUNCHER_STACK_RELEASE_INITIAL_SPREAD_PERCENT, 0, 200, "%");
        addStackParam(content, "桌面入口显示数量", "从桌面进入后台时，轻量入场阶段参与显示的卡片数量。",
                SettingsStore.KEY_LAUNCHER_STACK_DESKTOP_ENTRY_VISIBLE_COUNT, 1, 10, "");
        addStackParam(content, "桌面入口锚点位置", "从桌面进入后台时，轻量入场阶段的起始锚点。",
                SettingsStore.KEY_LAUNCHER_STACK_DESKTOP_ENTRY_ANCHOR_INDEX, 0, 9, "");
        addStackParam(content, "手势松手回弹时长", "上滑进入后台后，堆叠回弹动画时长。",
                SettingsStore.KEY_LAUNCHER_STACK_GESTURE_RELEASE_DURATION_MS, 60, 1500, "ms");

        content = addSection(root, "滑动展开范围", "左侧表示主卡片左边退走的卡片，右侧表示主卡片右边露出的卡片。");
        addStackParam(content, "常驻显示范围", "左右滑动稳定后，锚点两侧参与显示的卡片范围。",
                SettingsStore.KEY_LAUNCHER_STACK_STABLE_VISIBLE_RADIUS, 1, 8, "");
        addStackParam(content, "入场显示范围", "进入最近任务轻量阶段，锚点两侧预先参与显示的卡片范围。",
                SettingsStore.KEY_LAUNCHER_STACK_ENTRY_LIGHT_RADIUS, 1, 6, "");
        addStackParam(content, "回弹显示范围", "手势松开回弹时，保持完整视觉计算的卡片范围。",
                SettingsStore.KEY_LAUNCHER_STACK_GESTURE_RELEASE_CORE_RADIUS, 1, 8, "");
        addStackParam(content, "AppFlow 入场显示范围", "应用进入最近任务过程中，保留动态 AppFlow 的卡片范围。",
                SettingsStore.KEY_LAUNCHER_STACK_APP_FLOW_LIGHT_RADIUS, 1, 8, "");
        addStackParam(content, "右侧卡片基础展开速度", "右侧卡片展开的基础加速量。",
                SettingsStore.KEY_LAUNCHER_STACK_RIGHT_BASE_SPEEDUP_PERCENT, 0, 120, "%");
        addStackParam(content, "右侧卡片深层展开速度", "越靠深层的右侧卡片展开越快。",
                SettingsStore.KEY_LAUNCHER_STACK_RIGHT_SPEEDUP_PERCENT, 0, 200, "%");

        content = addSection(root, "左右滑动手感", "调整手指拖动、翻页判断和松手吸附。默认值保持 Flyme 原有手感。");
        addStackParam(content, "拖动阻力", "数值越大，卡片跟随手指移动得越慢。",
                SettingsStore.KEY_LAUNCHER_STACK_HORIZONTAL_DRAG_RESISTANCE_PERCENT, -100, 95, "%");
        addStackParam(content, "翻页距离阈值", "数值越大，需要拖动更远才会切换任务。",
                SettingsStore.KEY_LAUNCHER_STACK_HORIZONTAL_PAGE_THRESHOLD_PERCENT, 1, 80, "%");
        addStackParam(content, "快滑速度阈值", "数值越低，短促快滑越容易切换任务。",
                SettingsStore.KEY_LAUNCHER_STACK_HORIZONTAL_FLING_VELOCITY_DP, 50, 3000, "dp/s");
        addStackParam(content, "松手吸附时长", "数值越大，卡片吸附到目标位置越慢。",
                SettingsStore.KEY_LAUNCHER_STACK_HORIZONTAL_SNAP_DURATION_MS, 50, 2500, "ms");

        content = addSection(root, "退出与启动动画", "调整返回桌面和点击任务启动应用时的动画距离。");
        addStackParam(content, "回桌面缩放量", "点空白回桌面时，卡片额外缩小的比例。",
                SettingsStore.KEY_LAUNCHER_STACK_BLANK_EXIT_SCALE_DELTA_PERCENT, 0, 50, "%");
        addStackParam(content, "回桌面额外位移", "点空白回桌面时，卡片额外滑出的距离。",
                SettingsStore.KEY_LAUNCHER_STACK_BLANK_EXIT_EXTRA_TRAVEL_PERCENT, 0, 150, "%");
        addStackParam(content, "启动旁侧退出距离", "点击任务启动时，旁边卡片额外移开的距离。",
                SettingsStore.KEY_LAUNCHER_STACK_TASK_LAUNCH_EXTRA_WIDTH_PERCENT, 0, 200, "%");

        content = addSection(root, "删除手势", "调整上滑删除任务的触发判断和动画。");
        addStackParam(content, "删除成功动画", "任务删除成功时滑出动画时长。",
                SettingsStore.KEY_LAUNCHER_STACK_DISMISS_SUCCESS_ANIM_MS, 30, 1000, "ms");
        addStackParam(content, "删除取消动画", "删除取消时回弹动画时长。",
                SettingsStore.KEY_LAUNCHER_STACK_DISMISS_CANCEL_ANIM_MS, 50, 1500, "ms");
        addStackParam(content, "删除重排动画", "删除后剩余卡片重排动画时长。",
                SettingsStore.KEY_LAUNCHER_STACK_DISMISS_RELAYOUT_ANIM_MS, 50, 1500, "ms");
        addStackParam(content, "拖动重排最大进度", "拖动删除过程中，列表提前重排的最大进度。",
                SettingsStore.KEY_LAUNCHER_STACK_DISMISS_DRAG_RELAYOUT_MAX_PERCENT, 0, 100, "%");
        addStackParam(content, "纵向删除判定强度", "数值越大，越要求纵向滑动占主导。",
                SettingsStore.KEY_LAUNCHER_STACK_DISMISS_SECONDARY_DOMINANCE_PERCENT, 20, 400, "%");
        addStackParam(content, "快速上滑删除阈值", "数值越低，快速上滑越容易触发删除。",
                SettingsStore.KEY_LAUNCHER_STACK_DISMISS_MIN_FLING_VELOCITY, 100, 6000, "");

        content = addSection(root, "下拉菜单", "调整下拉任务卡片打开系统菜单所需的距离。");
        addStackParam(content, "菜单触发距离", "数值越大，需要下拉更远才会弹出任务菜单。",
                SettingsStore.KEY_LAUNCHER_STACK_MENU_PULL_THRESHOLD_DP, 10, 400, "dp");

        content = addSection(root, "卡片视觉效果", "调整边缘卡片的模糊效果和隐藏阈值。");
        addStackParam(content, "阴影强度", "数值越大，卡片周围的系统阴影越明显。",
                SettingsStore.KEY_LAUNCHER_STACK_SHADOW_ELEVATION_DP, 0, 16, "dp");
        addStackParam(content, "最大模糊强度", "边缘卡片截图和图标的最大模糊强度。",
                SettingsStore.KEY_LAUNCHER_STACK_CONTENT_MAX_BLUR_DP, 0, 80, "dp");
        addStackParam(content, "开始模糊的透明度", "卡片透明度低于此值后开始增加模糊效果。",
                SettingsStore.KEY_LAUNCHER_STACK_CONTENT_BLUR_START_ALPHA_PERCENT, 0, 100, "%");
        addStackParam(content, "左侧卡片淡出距离", "数值越大，左侧卡片会在更长距离内逐渐淡出。",
                SettingsStore.KEY_LAUNCHER_STACK_LEFT_FADE_DISTANCE_PERCENT, 1, 150, "%");
        addStackParam(content, "标题提前淡出距离", "数值越大，任务标题越早开始渐变，并在卡片开始淡出前消失。",
                SettingsStore.KEY_LAUNCHER_STACK_TITLE_FADE_DISTANCE_PERCENT, 100, 300, "%");
        addStackParam(content, "左侧卡片隐藏阈值", "左侧卡片透明度低于此值后不再参与显示。",
                SettingsStore.KEY_LAUNCHER_STACK_LEFT_RELEASE_ALPHA_THRESHOLD_PERCENT, 0, 100, "%");

        content = addSection(root, "动画性能", "调整滑动时的刷新率和资源释放延迟。");
        addStackParam(content, "滑动刷新率", "最近任务滑动时向系统请求的刷新率。",
                SettingsStore.KEY_LAUNCHER_STACK_SCROLL_FRAME_RATE, 30, 240, "Hz");
        addStackParam(content, "帧率释放延迟", "停止滑动后恢复系统默认帧率的延迟。",
                SettingsStore.KEY_LAUNCHER_STACK_FRAME_RATE_RELEASE_DELAY_MS, 0, 30000, "ms");

        content = addSection(root, "参数备份与恢复", "");
        activity.addActionButtonRow(content, "导出最近任务参数",
                "复制当前最近任务布局参数 JSON。",
                "导出", activity::exportLauncherStackParamsToClipboard);
        activity.addDivider(content);
        activity.addActionButtonRow(content, "导入最近任务参数",
                "粘贴最近任务布局参数 JSON 并写入当前设置。",
                "导入", activity::showImportLauncherStackParamsDialog);
        activity.addDivider(content);
        activity.addActionButtonRow(content, "恢复全部默认参数",
                "只恢复本页最近任务参数，不影响功能开关。",
                "恢复", activity::resetLauncherStackParams);
        return root;
    }

    private LinearLayout addSection(LinearLayout root, String title, String subtitle) {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(activity.buildSectionCard(title, subtitle, content),
                root.getChildCount() == 0 ? activity.matchWrap() : activity.matchWrapWithTop(8));
        return content;
    }

    private void addStackParam(LinearLayout content, String title, String subtitle,
            String key, int min, int max, String suffix) {
        activity.addDefaultableSliderRow(
                content,
                title,
                subtitle,
                key,
                SettingsStore.defaultInt(key),
                min,
                max,
                suffix);
    }

    public View createOneMindPerfControlCard() {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        activity.addSwitchRow(content, "禁用 OneMind 性能调节",
                "开启后阻断 com.meizu.pps 的 CPU/GPU 性能锁和线程绑核，热控和刷新率不动。",
                SettingsStore.KEY_ONEMIND_PERF_DISABLE_ENABLED,
                SettingsStore.DEFAULT_ONEMIND_PERF_DISABLE_ENABLED);
        activity.addDivider(content);
        activity.addSwitchRow(content, "OneMind 日志输出",
                "开启后把 PPS Hook 加载、调用入参和覆盖返回写入 logcat。",
                SettingsStore.KEY_ONEMIND_LOGCAT_ENABLED,
                SettingsStore.DEFAULT_ONEMIND_LOGCAT_ENABLED);
        activity.addDivider(content);
        TextView status = new TextView(activity);
        status.setText("未检测");
        status.setTextColor(activity.subtextColor());
        status.setTextSize(13);
        content.addView(status, activity.matchWrap());
        activity.addDivider(content);
        activity.addActionButtonRow(content, "Hook 点检测",
                "检查当前系统 com.meizu.pps 里目标类和方法是否仍存在。",
                "检测", () -> activity.detectOneMindHookPoints(status));
        return activity.buildSectionCard(
                "OneMind 性能设置",
                "只控制 PPS 的性能下发，不调整温控。",
                content);
    }

    public View createMzSafeOptimizationCard() {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        activity.addSwitchRow(content, "手机管家后台优化",
                "阻止安装处置周期任务和自动垃圾扫描；保留存储碎片优化，但关闭统计上传和失败重试。",
                SettingsStore.KEY_MZ_SAFE_BACKGROUND_OPTIMIZATION_ENABLED,
                SettingsStore.DEFAULT_MZ_SAFE_BACKGROUND_OPTIMIZATION_ENABLED);
        return activity.buildSectionCard(
                "手机管家后台优化",
                "不影响电话短信拦截和流量统计。",
                content);
    }

    public View createPositionTuningSettingsCard() {
        activity.positionTuningSliderBindings().clear();

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout content = addSection(root, "时钟与通知区域", "调整状态栏时钟右侧的间距，会影响时间和通知图标之间的位置。");
        activity.addPositionOffsetSliderRow(content, "时钟右边距",
                "基于系统默认间距做增减。系统原本的右侧边距为 2dp。正数增大间距，负数减小间距。",
                SettingsStore.KEY_CLOCK_RIGHT_PADDING_OFFSET_DP,
                SettingsStore.DEFAULT_CLOCK_RIGHT_PADDING_OFFSET_DP * 10);

        content = addSection(root, "电池", "下面 3 项只在模块接管电池绘制后生效。");
        activity.addPositionOffsetSliderRow(content, "电池图标",
                "整体电池轮廓的 Y 轴位置。默认 0dp。",
                SettingsStore.KEY_BATTERY_ICON_Y_OFFSET_DP,
                SettingsStore.DEFAULT_BATTERY_ICON_Y_OFFSET_DP * 10);
        activity.addDivider(content);
        activity.addPositionOffsetSliderRow(content, "电池内电量数字",
                "只改电池内部数字的基线高度。默认 0dp。",
                SettingsStore.KEY_BATTERY_TEXT_Y_OFFSET_DP,
                SettingsStore.DEFAULT_BATTERY_TEXT_Y_OFFSET_DP * 10);
        activity.addDivider(content);
        activity.addPositionOffsetSliderRow(content, "闪电图标",
                "只改充电 / 快充闪电图标的 Y 轴位置。默认 0dp。",
                SettingsStore.KEY_BATTERY_BOLT_Y_OFFSET_DP,
                SettingsStore.DEFAULT_BATTERY_BOLT_Y_OFFSET_DP * 10);

        content = addSection(root, "移动网络", "这些项只影响模块绘制的移动网络图标和 5G/5GA 标签。");
        activity.addPositionOffsetSliderRow(content, "单层信号图标",
                "单卡场景下信号柱的 Y 轴位置。默认 0dp。",
                SettingsStore.KEY_SIGNAL_SINGLE_Y_OFFSET_DP,
                SettingsStore.DEFAULT_SIGNAL_SINGLE_Y_OFFSET_DP * 10);
        activity.addDivider(content);
        activity.addPositionOffsetSliderRow(content, "5G / 5GA 标识",
                "只改 5G / 5GA 文本标识的 Y 轴位置。默认 0dp。",
                SettingsStore.KEY_SIGNAL_BADGE_Y_OFFSET_DP,
                SettingsStore.DEFAULT_SIGNAL_BADGE_Y_OFFSET_DP * 10);
        activity.addDivider(content);
        activity.addPositionOffsetSliderRow(content, "双层信号图标",
                "双卡合一场景下整组信号图形的 Y 轴位置。默认 0dp。",
                SettingsStore.KEY_SIGNAL_DUAL_Y_OFFSET_DP,
                SettingsStore.DEFAULT_SIGNAL_DUAL_Y_OFFSET_DP * 10);

        content = addSection(root, "Wi-Fi", "只改模块自绘的 Wi-Fi 图标。默认 0dp。");
        activity.addPositionOffsetSliderRow(content, "Wi-Fi 图标",
                "Wi-Fi 图标整体的 Y 轴位置。默认 0dp。",
                SettingsStore.KEY_WIFI_Y_OFFSET_DP,
                SettingsStore.DEFAULT_WIFI_Y_OFFSET_DP * 10);

        content = addSection(root, "输入法控制栏", "这里单独调整输入法控制栏的位置。");
        activity.addPositionOffsetSliderRow(content, "输入法控制栏抬高",
                "正数向上、负数向下；滑块按 1dp 粗调，点右侧数值可输入 0.1dp。",
                SettingsStore.KEY_IME_CONTROL_BAR_Y_OFFSET_DP,
                SettingsStore.DEFAULT_IME_CONTROL_BAR_Y_OFFSET_DP * 10);

        content = addSection(root, "应用与重置", "");
        activity.addActionButtonRow(content, "应用当前调整",
                "将本页待应用的调整一次性保存，并刷新当前状态栏。",
                "应用", activity::applyAllPositionOffsets);
        activity.addDivider(content);
        activity.addActionButtonRow(content, "所有位置恢复 0",
                "将本页位置调整设为 0；然后点击“应用当前调整”使其生效。",
                "归零", activity::resetAllPositionOffsets);
        return root;
    }

    public View createCameraCirclePositionCard() {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        activity.addTenthDpSliderRow(content, "横向偏移",
                "正数向右移动，负数向左移动，最小单位 0.01dp。",
                SettingsStore.KEY_CAMERA_CIRCLE_BATTERY_X_OFFSET_DP,
                SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_X_OFFSET_DP * 100,
                SettingsStore.CAMERA_CIRCLE_BATTERY_OFFSET_MIN_TENTH_DP,
                SettingsStore.CAMERA_CIRCLE_BATTERY_OFFSET_MAX_TENTH_DP);
        activity.addDivider(content);
        activity.addTenthDpSliderRow(content, "纵向偏移",
                "正数向下移动，负数向上移动，最小单位 0.01dp。",
                SettingsStore.KEY_CAMERA_CIRCLE_BATTERY_Y_OFFSET_DP,
                SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_Y_OFFSET_DP * 100,
                SettingsStore.CAMERA_CIRCLE_BATTERY_OFFSET_MIN_TENTH_DP,
                SettingsStore.CAMERA_CIRCLE_BATTERY_OFFSET_MAX_TENTH_DP);
        return activity.buildSectionCard(
                "环形电池位置",
                "偏移量会叠加到系统识别出的摄像头挖孔中心。",
                content);
    }

    public View createTelephonyDebugSettingsCard() {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout content = addSection(root, "模拟开关",
                "开启后，模拟状态会立即同步到 SystemUI。请先设置测试卡，再选择 SIM 卡数量和默认上网卡。");
        activity.addSwitchRow(content, "启用移动网络状态模拟",
                "关闭后恢复真实移动网络状态，已保存的模拟参数会保留。",
                SettingsStore.KEY_TELEPHONY_DEBUG_ENABLED,
                SettingsStore.DEFAULT_TELEPHONY_DEBUG_ENABLED);
        activity.addDivider(content);
        activity.addChoiceRow(content, "SIM 卡数量",
                "用于测试无卡、单卡和双卡时移动网络图标的布局和显示。",
                SettingsStore.KEY_TELEPHONY_DEBUG_SIM_COUNT,
                SettingsStore.DEFAULT_TELEPHONY_DEBUG_SIM_COUNT,
                new int[]{0, 1, 2},
                new String[]{"0 张", "1 张", "2 张"});
        activity.addDivider(content);
        activity.addChoiceRow(content, "默认移动数据卡",
                "双卡时，网络类型和 5G 标签跟随这里选择的 SIM 卡。",
                SettingsStore.KEY_TELEPHONY_DEBUG_DEFAULT_DATA_SLOT,
                SettingsStore.DEFAULT_TELEPHONY_DEBUG_DEFAULT_DATA_SLOT,
                new int[]{
                        SettingsStore.TELEPHONY_DEBUG_DEFAULT_DATA_SLOT_NONE,
                        SettingsStore.TELEPHONY_DEBUG_DEFAULT_DATA_SLOT_CARD1,
                        SettingsStore.TELEPHONY_DEBUG_DEFAULT_DATA_SLOT_CARD2
                },
                new String[]{"无", "卡 1", "卡 2"});

        addTelephonyDebugSlotSection(root,
                "卡 1",
                "第一张测试卡。单卡场景默认看它；双卡合并图标时，上层柱读取它的信号等级。",
                SettingsStore.KEY_TELEPHONY_DEBUG_SLOT1_NETWORK_PROFILE,
                SettingsStore.DEFAULT_TELEPHONY_DEBUG_SLOT1_NETWORK_PROFILE,
                SettingsStore.KEY_TELEPHONY_DEBUG_SLOT1_SIGNAL_LEVEL,
                SettingsStore.DEFAULT_TELEPHONY_DEBUG_SLOT1_SIGNAL_LEVEL);

        addTelephonyDebugSlotSection(root,
                "卡 2",
                "第二张测试卡。只有插卡数量切到 2 张时才会参与模拟；双卡合并图标时，下层圆点读取它的信号等级。",
                SettingsStore.KEY_TELEPHONY_DEBUG_SLOT2_NETWORK_PROFILE,
                SettingsStore.DEFAULT_TELEPHONY_DEBUG_SLOT2_NETWORK_PROFILE,
                SettingsStore.KEY_TELEPHONY_DEBUG_SLOT2_SIGNAL_LEVEL,
                SettingsStore.DEFAULT_TELEPHONY_DEBUG_SLOT2_SIGNAL_LEVEL);

        content = addSection(root, "恢复设置", "");
        activity.addActionButtonRow(content, "恢复真实移动网络状态",
                "关闭状态模拟，但保留两张测试卡的模拟参数。",
                "恢复", activity::disableTelephonyDebug);
        return root;
    }

    private LinearLayout buildNotificationAppIconOptions() {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(activity);
        title.setText("通知图标尺寸");
        title.setTextColor(activity.primaryColor());
        title.setTextSize(13);
        card.addView(title, activity.matchWrap());

        activity.addDivider(card);
        activity.addApplySliderRow(card, "通知图标大小",
                "改的是状态栏里这个通知图标 View 占用的宽度，状态栏高度保持系统原来的值。",
                SettingsStore.KEY_NOTIFICATION_APP_ICON_SIZE_DP,
                SettingsStore.DEFAULT_NOTIFICATION_APP_ICON_SIZE_DP,
                12, 28, "dp");
        activity.addDivider(card);
        activity.addApplySliderRow(card, "图标容器内边距",
                "改这个图标 View 的 padding。数值越大，图标会更靠中间。",
                SettingsStore.KEY_NOTIFICATION_APP_ICON_PADDING_DP,
                SettingsStore.DEFAULT_NOTIFICATION_APP_ICON_PADDING_DP,
                0, 8, "dp");

        // ANIP only swaps the *source* of the icon; the sizing controls above still apply to it.
        activity.addDivider(card);
        LinearLayout anipHeader = new LinearLayout(activity);
        anipHeader.setOrientation(LinearLayout.VERTICAL);
        TextView anipTitle = new TextView(activity);
        anipTitle.setText("通知图标来源");
        anipTitle.setTextColor(activity.primaryColor());
        anipTitle.setTextSize(13);
        anipHeader.addView(anipTitle);
        card.addView(anipHeader, activity.matchWrapWithTop(2));

        LinearLayout anipStatusRow = new LinearLayout(activity);
        anipStatusRow.setOrientation(LinearLayout.VERTICAL);
        TextView anipStatus = new TextView(activity);
        anipStatus.setTextColor(activity.subtextColor());
        anipStatus.setTextSize(11);
        anipStatus.setLineSpacing(activity.dp(3), 1f);
        anipStatusRow.addView(anipStatus);
        anipStatusRow.setVisibility(View.GONE);

        activity.addSwitchRow(card, "优先使用 ANIP 图标库",
                "开启后，图标库已收录的应用改用 ANIP 的单色通知图标；未收录的应用仍使用桌面图标。"
                        + "默认关闭。",
                SettingsStore.KEY_ANIP_ICON_ENABLED,
                SettingsStore.DEFAULT_ANIP_ICON_ENABLED,
                (buttonView, isChecked) -> {
                    anipStatusRow.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                    updateAnipIconLibraryStatus(anipStatus);
                });
        card.addView(anipStatusRow, activity.matchWrapWithTop(2));

        activity.addActionButtonRow(card, "通知图标库",
                "查看已收录的应用、搜索名称或包名，并单独指定某个应用用哪种图标。",
                "打开", () -> new AnipIconLibraryEditor(activity).show());

        updateAnipIconLibraryStatus(anipStatus);
        anipStatusRow.setVisibility(SettingsStore.readBoolean(
                activity.prefs(),
                SettingsStore.KEY_ANIP_ICON_ENABLED,
                SettingsStore.DEFAULT_ANIP_ICON_ENABLED) ? View.VISIBLE : View.GONE);
        return card;
    }

    /**
     * Refreshes the ANIP summary line.
     *
     * <p>The library parses its manifest from the APK, so this must not run while the card is being
     * built on the main thread; the count is filled in from a background read.
     */
    private void updateAnipIconLibraryStatus(TextView status) {
        if (status == null) {
            return;
        }
        status.setText("正在读取图标库…");
        final android.content.Context appContext = activity.getApplicationContext();
        new Thread(() -> {
            String text;
            try {
                AnipIconLibrary library = AnipIconLibrary.get();
                AnipBundleStore.Installed installed = AnipBundleStore.resolve(appContext);
                library.load(installed == null ? null : installed.directory);
                int available = library.getRuleCount();
                int overrides = AnipIconMode.readOverrides(activity.prefs()).size();
                if (available <= 0) {
                    text = "尚未下载图标库。点下方“通知图标库”打开后可以下载。";
                } else {
                    text = "图标库已收录 " + available + " 个应用";
                    if (overrides > 0) {
                        text += "，其中 " + overrides + " 个已单独指定";
                    }
                    text += "。";
                }
            } catch (Throwable ignored) {
                text = "图标库读取失败，请重试。";
            }
            final String result = text;
            activity.runOnUiThread(() -> status.setText(result));
        }, "anip-icon-library-status").start();
    }

    private View buildStatusBarIconScaleCard() {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);

        activity.addApplySliderRow(content, "状态栏图标大小",
                "默认值为 100%。调整右上角状态图标，以及模块接管后的电池和移动网络图标。通知图标、隐私权限图标和隐私圆点不在此处调整。",
                SettingsStore.KEY_STATUS_BAR_ICON_SCALE_PERCENT,
                SettingsStore.DEFAULT_STATUS_BAR_ICON_SCALE_PERCENT, 50, 200, "%");
        batterySizingOptions = new LinearLayout(activity);
        batterySizingOptions.setOrientation(LinearLayout.VERTICAL);
        activity.addDivider(batterySizingOptions);
        activity.addApplySliderRow(batterySizingOptions, "电池内部数字大小",
                "只改电池图标内部的电量数字。默认 100%。",
                SettingsStore.KEY_BATTERY_INNER_TEXT_SCALE_PERCENT,
                SettingsStore.DEFAULT_BATTERY_INNER_TEXT_SCALE_PERCENT, 50, 200, "%");
        content.addView(batterySizingOptions, PageViewUtils.matchWrap());
        updateBatteryOptionsVisibility();
        return activity.buildSectionCard("图标大小",
                "调整右上角状态图标，以及模块接管后的电池和移动网络图标。通知图标、隐私权限图标和隐私圆点不在此处调整。",
                content);
    }

    private LinearLayout buildMBackActionPage() {
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);

        activity.addProfileSectionHeader(page, "长按动作",
                "只接管 MBack 长按操作，保留单击和其他系统操作。 ");
        activity.addSwitchRow(page, "接管 MBack 长按",
                "接管 Flyme SystemUI 中原本唤醒 Aicy 的 MBack/Home 长按入口，改为执行下方设置的动作。",
                SettingsStore.KEY_MBACK_LONG_TOUCH_URL_ENABLED,
                SettingsStore.DEFAULT_MBACK_LONG_TOUCH_URL_ENABLED);
        activity.addDivider(page);
        activity.addChoiceRow(page, "长触动作",
                "可选发送 URL / Intent、底部时间弹窗，或者后台应用星图。",
                SettingsStore.KEY_MBACK_LONG_TOUCH_ACTION,
                SettingsStore.DEFAULT_MBACK_LONG_TOUCH_ACTION,
                new int[]{
                        SettingsStore.MBACK_LONG_TOUCH_ACTION_INTENT_URI,
                        SettingsStore.MBACK_LONG_TOUCH_ACTION_CLOCK_POPUP,
                        SettingsStore.MBACK_LONG_TOUCH_ACTION_STAR_APPS
                },
                new String[]{"URL / Intent", "底部时间弹窗", "后台应用星图"});
        activity.addDivider(page);
        activity.addTextSettingRow(page, "目标 URL / Intent URI",
                "只在“URL / Intent”模式下生效。支持 https://、自定义 scheme 和 intent:// URI。点击右侧内容编辑，留空则回退原始 AICY 行为。",
                SettingsStore.KEY_MBACK_LONG_TOUCH_INTENT_URI,
                SettingsStore.DEFAULT_MBACK_LONG_TOUCH_INTENT_URI,
                "未设置");
        activity.addDivider(page);
        activity.addActionButtonRow(page, "测试 URL / Intent",
                "只测试当前填写的 URL / Intent URI，不测试底部时间弹窗模式。",
                "立即测试", activity::testLaunchMBackIntent);
        return page;
    }

    private LinearLayout buildWindowModeSideGesturePage() {
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);

        activity.addProfileSectionHeader(page, "小窗选择面板",
                "这里的设置只影响 Flyme 原生小窗面板，不影响多应用工作台。");
        activity.addSwitchRow(page, "预热原生小窗面板",
                "提前准备 Flyme 原生小窗选择面板，降低首次触发卡顿。",
                SettingsStore.KEY_WINDOWMODE_SIDE_GESTURE_PREWARM_ENABLED,
                SettingsStore.DEFAULT_WINDOWMODE_SIDE_GESTURE_PREWARM_ENABLED);
        activity.addDivider(page);
        LinearLayout hoverFullscreenOptions = buildWindowModeHoverFullscreenOptions();
        boolean hoverFullscreenEnabled = SettingsStore.readBoolean(
                activity.prefs(),
                SettingsStore.KEY_WINDOWMODE_HOVER_FULLSCREEN_ENABLED,
                SettingsStore.DEFAULT_WINDOWMODE_HOVER_FULLSCREEN_ENABLED);
        hoverFullscreenOptions.setVisibility(hoverFullscreenEnabled ? View.VISIBLE : View.GONE);
        activity.addSwitchRow(page, "长停全屏打开",
                "手指在小窗选择面板某个应用上停留达到阈值后，抬手改为全屏打开。",
                SettingsStore.KEY_WINDOWMODE_HOVER_FULLSCREEN_ENABLED,
                SettingsStore.DEFAULT_WINDOWMODE_HOVER_FULLSCREEN_ENABLED,
                (buttonView, isChecked) -> hoverFullscreenOptions.setVisibility(
                        isChecked ? View.VISIBLE : View.GONE));
        LinearLayout.LayoutParams hoverFullscreenOptionsLp = PageViewUtils.matchWrap();
        hoverFullscreenOptionsLp.leftMargin = activity.dp(12);
        page.addView(hoverFullscreenOptions, hoverFullscreenOptionsLp);
        activity.addDivider(page);
        LinearLayout twoRingOptions = buildWindowModeTwoRingOptions();
        boolean twoRingEnabled = SettingsStore.readBoolean(
                activity.prefs(),
                SettingsStore.KEY_WINDOWMODE_TWO_RING_LAUNCHER_ENABLED,
                SettingsStore.DEFAULT_WINDOWMODE_TWO_RING_LAUNCHER_ENABLED);
        twoRingOptions.setVisibility(twoRingEnabled ? View.VISIBLE : View.GONE);
        activity.addSwitchRow(page, "小窗环数扩展",
                "扩展 Flyme 原生小窗选择面板的环数；关闭后使用系统原生界面。",
                SettingsStore.KEY_WINDOWMODE_TWO_RING_LAUNCHER_ENABLED,
                SettingsStore.DEFAULT_WINDOWMODE_TWO_RING_LAUNCHER_ENABLED,
                (buttonView, isChecked) -> twoRingOptions.setVisibility(
                        isChecked ? View.VISIBLE : View.GONE));
        LinearLayout.LayoutParams twoRingOptionsLp = PageViewUtils.matchWrap();
        twoRingOptionsLp.leftMargin = activity.dp(12);
        page.addView(twoRingOptions, twoRingOptionsLp);
        activity.addDivider(page);
        LinearLayout sideGestureOptions = new LinearLayout(activity);
        sideGestureOptions.setOrientation(LinearLayout.VERTICAL);
        boolean sideGestureEnabled = SettingsStore.readBoolean(
                activity.prefs(),
                SettingsStore.KEY_WINDOWMODE_SIDE_GESTURE_ENABLED,
                SettingsStore.DEFAULT_WINDOWMODE_SIDE_GESTURE_ENABLED);
        sideGestureOptions.setVisibility(sideGestureEnabled ? View.VISIBLE : View.GONE);
        activity.addSwitchRow(page, "接管小窗侧边手势",
                "拦截屏幕左右侧边的小窗触发手势，改为执行这里配置的动作。",
                SettingsStore.KEY_WINDOWMODE_SIDE_GESTURE_ENABLED,
                SettingsStore.DEFAULT_WINDOWMODE_SIDE_GESTURE_ENABLED,
                (buttonView, isChecked) -> sideGestureOptions.setVisibility(
                        isChecked ? View.VISIBLE : View.GONE));
        activity.addDivider(sideGestureOptions);
        activity.addChoiceRow(sideGestureOptions, "触发动作",
                "发送 URL / Intent。",
                SettingsStore.KEY_WINDOWMODE_SIDE_GESTURE_ACTION,
                SettingsStore.DEFAULT_WINDOWMODE_SIDE_GESTURE_ACTION,
                new int[]{
                        SettingsStore.MBACK_LONG_TOUCH_ACTION_INTENT_URI
                },
                new String[]{"URL / Intent"});
        activity.addDivider(sideGestureOptions);
        activity.addTextSettingRow(sideGestureOptions, "目标 URL / Intent URI",
                "只在“URL / Intent”模式下生效。支持 https://、自定义 scheme 和 intent:// URI。留空则回退 Flyme 小窗面板。",
                SettingsStore.KEY_WINDOWMODE_SIDE_GESTURE_INTENT_URI,
                SettingsStore.DEFAULT_WINDOWMODE_SIDE_GESTURE_INTENT_URI,
                "未设置");
        activity.addDivider(sideGestureOptions);
        activity.addActionButtonRow(sideGestureOptions, "测试 URL / Intent",
                "只测试当前填写的 URL / Intent URI。",
                "立即测试", activity::testLaunchWindowModeSideGestureIntent);
        LinearLayout.LayoutParams sideGestureOptionsLp = PageViewUtils.matchWrap();
        sideGestureOptionsLp.leftMargin = activity.dp(12);
        page.addView(sideGestureOptions, sideGestureOptionsLp);
        return page;
    }

    private LinearLayout buildWindowModeTwoRingOptions() {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(activity);
        title.setText("环数扩展设置");
        title.setTextColor(activity.primaryColor());
        title.setTextSize(13);
        card.addView(title, activity.matchWrap());

        activity.addDivider(card);
        activity.addApplySliderRow(card, "外环应用数量",
                "最外环显示的应用数量，默认 7。",
                SettingsStore.KEY_WINDOWMODE_TWO_RING_OUTER_APP_COUNT,
                SettingsStore.DEFAULT_WINDOWMODE_TWO_RING_OUTER_APP_COUNT,
                0, SettingsStore.DEFAULT_WINDOWMODE_TWO_RING_OUTER_APP_COUNT, " 个");
        activity.addDivider(card);
        activity.addApplySliderRow(card, "内环应用数量",
                "内环显示的普通应用数量，默认 4。",
                SettingsStore.KEY_WINDOWMODE_TWO_RING_INNER_APP_COUNT,
                SettingsStore.DEFAULT_WINDOWMODE_TWO_RING_INNER_APP_COUNT,
                0, SettingsStore.DEFAULT_WINDOWMODE_TWO_RING_INNER_APP_COUNT, " 个");
        activity.addDivider(card);
        activity.addApplySliderRow(card, "内环图标大小",
                "只改小窗环数扩展内环的应用图标大小，默认 100%。",
                SettingsStore.KEY_WINDOWMODE_TWO_RING_INNER_ICON_SCALE_PERCENT,
                SettingsStore.DEFAULT_WINDOWMODE_TWO_RING_INNER_ICON_SCALE_PERCENT,
                10, 100, "%");
        activity.addDivider(card);
        activity.addApplySliderRow(card, "内环半径",
                "内环到手势中心的距离，默认 62%。",
                SettingsStore.KEY_WINDOWMODE_TWO_RING_INNER_RADIUS_PERCENT,
                SettingsStore.DEFAULT_WINDOWMODE_TWO_RING_INNER_RADIUS_PERCENT,
                10, 100, "%");
        activity.addDivider(card);
        LinearLayout recentOptions = new LinearLayout(activity);
        recentOptions.setOrientation(LinearLayout.VERTICAL);
        boolean recentEnabled = SettingsStore.readBoolean(
                activity.prefs(),
                SettingsStore.KEY_WINDOWMODE_RECENT_INNER_RING_ENABLED,
                SettingsStore.DEFAULT_WINDOWMODE_RECENT_INNER_RING_ENABLED);
        recentOptions.setVisibility(recentEnabled ? View.VISIBLE : View.GONE);
        activity.addSwitchRow(card, "最内环显示最近使用应用",
                "从最近任务中补充可小窗应用，并过滤当前面板已有应用。",
                SettingsStore.KEY_WINDOWMODE_RECENT_INNER_RING_ENABLED,
                SettingsStore.DEFAULT_WINDOWMODE_RECENT_INNER_RING_ENABLED,
                (buttonView, isChecked) -> recentOptions.setVisibility(
                        isChecked ? View.VISIBLE : View.GONE));
        activity.addDivider(recentOptions);
        activity.addApplySliderRow(recentOptions, "最内环应用数量",
                "最近使用应用这一环显示的应用数量，默认 4。",
                SettingsStore.KEY_WINDOWMODE_RECENT_INNER_RING_APP_COUNT,
                SettingsStore.DEFAULT_WINDOWMODE_RECENT_INNER_RING_APP_COUNT,
                0, SettingsStore.DEFAULT_WINDOWMODE_RECENT_INNER_RING_APP_COUNT, " 个");
        activity.addDivider(recentOptions);
        activity.addApplySliderRow(recentOptions, "最内环图标大小",
                "只改最近使用应用这一环的图标大小，默认 100%。",
                SettingsStore.KEY_WINDOWMODE_RECENT_INNER_RING_ICON_SCALE_PERCENT,
                SettingsStore.DEFAULT_WINDOWMODE_RECENT_INNER_RING_ICON_SCALE_PERCENT,
                10, 100, "%");
        activity.addDivider(recentOptions);
        activity.addApplySliderRow(recentOptions, "最内环半径",
                "最内环到手势中心的距离，默认 38%。",
                SettingsStore.KEY_WINDOWMODE_RECENT_INNER_RING_RADIUS_PERCENT,
                SettingsStore.DEFAULT_WINDOWMODE_RECENT_INNER_RING_RADIUS_PERCENT,
                10, 100, "%");
        LinearLayout.LayoutParams recentOptionsLp = PageViewUtils.matchWrap();
        recentOptionsLp.leftMargin = activity.dp(12);
        card.addView(recentOptions, recentOptionsLp);
        return card;
    }

    private LinearLayout buildWindowModeHoverFullscreenOptions() {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);

        activity.addSliderRow(card, "触发阈值",
                "停留达到该时间后，抬手全屏打开。",
                SettingsStore.KEY_WINDOWMODE_HOVER_FULLSCREEN_TIMEOUT_MS,
                SettingsStore.DEFAULT_WINDOWMODE_HOVER_FULLSCREEN_TIMEOUT_MS,
                300, 2000, "ms");
        return card;
    }

    private LinearLayout buildMBackImmersivePage() {
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);

        activity.addProfileSectionHeader(page, "导航栏背景与抬高",
                "调整导航栏透明背景、白条和 MBack 背景位置。“抬高”也会影响应用对底部区域的判断。");
        activity.addSwitchRow(page, "导航栏背景透明",
                "将 MBack 所在导航栏背景设为透明，只改变背景，不改变 MBack 图标本身。",
                SettingsStore.KEY_MBACK_NAV_BAR_TRANSPARENT,
                SettingsStore.DEFAULT_MBACK_NAV_BAR_TRANSPARENT);
        activity.addDivider(page);
        activity.addSwitchRow(page, "隐藏导航栏白条",
                "只隐藏 MBack 绘制的白色胶囊，不改变长按逻辑和背景位置。",
                SettingsStore.KEY_MBACK_HIDE_PILL,
                SettingsStore.DEFAULT_MBACK_HIDE_PILL);
        activity.addDivider(page);
        activity.addApplyInsetSliderRow(page, "小白条长度",
                "调整白条长度。-1 表示保持系统默认。",
                SettingsStore.KEY_MBACK_PILL_LENGTH,
                SettingsStore.DEFAULT_MBACK_PILL_LENGTH, -1, 200);
        activity.addDivider(page);
        activity.addApplyInsetSliderRow(page, "小白条粗细",
                "调整白条粗细，不改变导航栏高度。-1 表示保持系统默认。",
                SettingsStore.KEY_MBACK_PILL_THICKNESS,
                SettingsStore.DEFAULT_MBACK_PILL_THICKNESS, -1, 12);
        activity.addDivider(page);
        activity.addSwitchRow(page, "触发区域与动画跟随",
                "保留系统外围容错范围，让触发区域和点击动画随小白条尺寸同步增减。",
                SettingsStore.KEY_MBACK_PILL_INTERACTION_SYNC_ENABLED,
                SettingsStore.DEFAULT_MBACK_PILL_INTERACTION_SYNC_ENABLED);
        activity.addDivider(page);
        activity.addApplyInsetSliderRow(page, "MBack 背景抬高",
                "调整 MBack 背景向上抬高的距离。-1 表示系统默认，0 表示不额外抬高；此设置也会影响应用感知到的底部区域。",
                SettingsStore.KEY_MBACK_INSET_SIZE,
                SettingsStore.DEFAULT_MBACK_INSET_SIZE, -1, 80);
        return page;
    }

    private LinearLayout buildMBackHeightPage() {
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);

        activity.addProfileSectionHeader(page, "导航栏高度",
                "调整 MBack 导航栏窗口高度，减少底部透明可触区域对应用按钮的遮挡。");
        activity.addApplyInsetSliderRow(page, "MBack 导航栏高度",
                "控制 MBack 导航栏窗口高度。-1 表示系统默认，数值越小，底部透明可触区域越矮。",
                SettingsStore.KEY_MBACK_NAV_BAR_HEIGHT,
                SettingsStore.DEFAULT_MBACK_NAV_BAR_HEIGHT, -1, 80);
        return page;
    }

    private LinearLayout buildConnectionRateThresholdPage() {
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);

        activity.addProfileSectionHeader(page, "自动显示与隐藏",
                "分别设置显示和隐藏阈值，连续满足条件后再切换，减少网速文字反复闪烁。");
        activity.addSwitchRow(page, "根据网速自动显示",
                "网速达到显示阈值时显示，低于隐藏阈值时隐藏。只影响显示，不改变采样。",
                SettingsStore.KEY_CONNECTION_RATE_AUTO_VISIBILITY_ENABLED,
                SettingsStore.DEFAULT_CONNECTION_RATE_AUTO_VISIBILITY_ENABLED);
        activity.addDivider(page);
        activity.addSliderRow(page, "显示阈值",
                "网速达到此值后显示，单位 KB/s。",
                SettingsStore.KEY_CONNECTION_RATE_SHOW_THRESHOLD_KB,
                SettingsStore.DEFAULT_CONNECTION_RATE_SHOW_THRESHOLD_KB, 0, 1024, "KB/s");
        activity.addDivider(page);
        activity.addSliderRow(page, "隐藏阈值",
                "网速低于此值后隐藏，单位 KB/s。",
                SettingsStore.KEY_CONNECTION_RATE_HIDE_THRESHOLD_KB,
                SettingsStore.DEFAULT_CONNECTION_RATE_HIDE_THRESHOLD_KB, 0, 1024, "KB/s");
        activity.addDivider(page);
        activity.addSliderRow(page, "显示确认次数",
                "连续达到显示阈值多少次后显示。",
                SettingsStore.KEY_CONNECTION_RATE_SHOW_SAMPLE_COUNT,
                SettingsStore.DEFAULT_CONNECTION_RATE_SHOW_SAMPLE_COUNT, 1, 5, "次");
        activity.addDivider(page);
        activity.addSliderRow(page, "隐藏确认次数",
                "连续低于隐藏阈值多少次后隐藏。",
                SettingsStore.KEY_CONNECTION_RATE_HIDE_SAMPLE_COUNT,
                SettingsStore.DEFAULT_CONNECTION_RATE_HIDE_SAMPLE_COUNT, 1, 5, "次");
        return page;
    }

    private LinearLayout buildTimeTypographyPage() {
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);

        activity.addProfileSectionHeader(page, "字体粗细与大小",
                "粗细设置也会影响系统电量数字，但不影响电池图标内部数字、网络标签和活动胶囊文字。");
        activity.addSwitchRow(page, "自定义文字粗细",
                "同步调整时间、日期、网速、锁屏运营商和系统电量数字。关闭后时间恢复常规粗细，其余恢复系统字体。",
                SettingsStore.KEY_CLOCK_BOLD_ENABLED, SettingsStore.DEFAULT_CLOCK_BOLD_ENABLED);
        activity.addDivider(page);
        activity.addApplySliderRow(page, "字体粗细",
                "数值越大越粗，范围 100–900；开启“自定义字体粗细”后生效。",
                SettingsStore.KEY_CLOCK_FONT_WEIGHT, SettingsStore.DEFAULT_CLOCK_FONT_WEIGHT, 100, 900, "");
        activity.addDivider(page);
        activity.addApplySliderRow(page, "文字大小",
                "缩放时间、日期、网速和锁屏运营商文字，100% 为系统默认大小；不影响系统电量数字。",
                SettingsStore.KEY_CLOCK_AND_CARRIER_TEXT_SIZE_PERCENT,
                SettingsStore.DEFAULT_CLOCK_AND_CARRIER_TEXT_SIZE_PERCENT, 50, 200, "%");
        activity.addDivider(page);
        activity.addSwitchRow(page, "锁屏大时钟",
                "使用模块绘制锁屏大时钟，目前只替换大数字区域。",
                SettingsStore.KEY_LOCKSCREEN_CANVAS_CLOCK_ENABLED,
                SettingsStore.DEFAULT_LOCKSCREEN_CANVAS_CLOCK_ENABLED);
        return page;
    }

    private LinearLayout buildTimeInteractionPage() {
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);

        activity.addProfileSectionHeader(page, "主状态栏时钟",
                "只影响左上角主状态栏时钟，不影响锁屏时钟和其他时间显示。");
        activity.addSwitchRow(page, "点击时钟显示详细信息",
                "点击左上角时钟显示时间和日期；在窗口内下拉可展开更多信息，上滑可收回。",
                SettingsStore.KEY_CLOCK_DETAIL_POPUP_ENABLED,
                SettingsStore.DEFAULT_CLOCK_DETAIL_POPUP_ENABLED);
        activity.addDivider(page);
        activity.addSwitchRow(page, "显示农历",
                "控制详细信息窗口是否显示农历日期。",
                SettingsStore.KEY_CLOCK_DETAIL_LUNAR_DATE_ENABLED,
                SettingsStore.DEFAULT_CLOCK_DETAIL_LUNAR_DATE_ENABLED);
        activity.addDivider(page);
        LinearLayout actionGridEditorSection = new LinearLayout(activity);
        actionGridEditorSection.setOrientation(LinearLayout.VERTICAL);
        boolean actionGridEnabled = SettingsStore.readBoolean(
                activity.prefs(),
                SettingsStore.KEY_CLOCK_DETAIL_ACTION_GRID_ENABLED,
                SettingsStore.DEFAULT_CLOCK_DETAIL_ACTION_GRID_ENABLED);
        actionGridEditorSection.setVisibility(actionGridEnabled ? View.VISIBLE : View.GONE);
        activity.addSwitchRow(page, "显示快捷入口图标",
                "在详细信息区域显示最多 5 个快捷入口；候选项来自 Aicy 纵览中可扫描到的入口。",
                SettingsStore.KEY_CLOCK_DETAIL_ACTION_GRID_ENABLED,
                SettingsStore.DEFAULT_CLOCK_DETAIL_ACTION_GRID_ENABLED,
                (buttonView, isChecked) -> actionGridEditorSection.setVisibility(
                        isChecked ? View.VISIBLE : View.GONE));
        activity.addDivider(actionGridEditorSection);
        activity.addActionButtonRow(actionGridEditorSection, "编辑快捷入口",
                "优先使用已缓存候选；可扫描/刷新 Aicy 纵览中的快捷入口。最多选择 5 个，长按已添加图标可排序，点击应用后保存。",
                "编辑", activity::showClockDetailActionGridEditor);
        page.addView(actionGridEditorSection, activity.matchWrap());
        return page;
    }

    private LinearLayout buildBatteryHollowOptions() {
        LinearLayout card = activity.card(activity.surfaceSoftColor(), 22);
        TextView title = new TextView(activity);
        title.setText("镂空电池");
        title.setTextColor(activity.primaryColor());
        title.setTextSize(13);
        card.addView(title, activity.matchWrap());
        activity.addDivider(card);
        activity.addSwitchRow(card, "电池内填充色随容量变化",
                "关闭时内部始终填满；开启后内部填充会按剩余电量缩短，未填充部分保留灰色底色。",
                SettingsStore.KEY_BATTERY_HOLLOW_FILL_FOLLOWS_LEVEL,
                SettingsStore.DEFAULT_BATTERY_HOLLOW_FILL_FOLLOWS_LEVEL);
        return card;
    }

    private void addTelephonyDebugSlotSection(LinearLayout root,
            String titleText,
            String subtitleText,
            String networkKey,
            int defaultNetworkValue,
            String signalKey,
            int defaultSignalValue) {
        LinearLayout content = addSection(root, titleText, subtitleText);
        activity.addChoiceRow(content, "网络类型",
                "设置移动网络类型。4G 会隐藏 5G 标签；5G、5G CA、5GA 和 5G+ 会显示对应状态。",
                networkKey,
                defaultNetworkValue,
                new int[]{
                        SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_OFFLINE,
                        SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_2G,
                        SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_3G,
                        SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_4G,
                        SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_5G,
                        SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_5G_CA,
                        SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_5GA,
                        SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_5G_PLUS
                },
                new String[]{"无服务", "2G", "3G", "4G", "5G", "5G CA", "5GA", "5G+"});
        activity.addDivider(content);
        activity.addChoiceRow(content, "信号强度",
                "设置 0 到 4 格的信号强度，移动网络图标会随之变化。",
                signalKey,
                defaultSignalValue,
                new int[]{0, 1, 2, 3, 4},
                new String[]{"0 格", "1 格", "2 格", "3 格", "4 格"});
    }

}
