package com.example.flymestatusbarsizer;

import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

final class SettingsCardFactory {
    private final MainActivity activity;

    SettingsCardFactory(MainActivity activity) {
        this.activity = activity;
    }

    View createIconSizingCard() {
        return buildStatusBarIconScaleCard();
    }

    View createBatterySettingsCard() {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);

        activity.addSwitchRow(content, "代码绘制电池图标",
                "关闭后恢复系统原来的电池图标，不再接管这一项的绘制和尺寸。",
                SettingsStore.KEY_BATTERY_CODE_DRAW_ENABLED,
                SettingsStore.DEFAULT_BATTERY_CODE_DRAW_ENABLED);
        activity.addDivider(content);
        activity.addChoiceRow(content, "电池图标样式",
                "当前保留类 IOS、类 One UI 和 IOS 旧版三套代码绘制样式。",
                SettingsStore.KEY_BATTERY_ICON_STYLE,
                SettingsStore.DEFAULT_BATTERY_ICON_STYLE,
                new int[]{SettingsStore.BATTERY_STYLE_IOS, SettingsStore.BATTERY_STYLE_ONEUI,
                        SettingsStore.BATTERY_STYLE_FLYME_CAPSULE},
                new String[]{"类 IOS", "类 One UI", "IOS 旧版"});
        activity.addDivider(content);
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
        activity.addSwitchRow(content, "电池内显示电量数字",
                "关闭后只保留图形电池，不在电池内部绘制剩余电量数字。",
                SettingsStore.KEY_BATTERY_LEVEL_TEXT_ENABLED,
                SettingsStore.DEFAULT_BATTERY_LEVEL_TEXT_ENABLED,
                (buttonView, isChecked) -> hollowSection.setVisibility(isChecked ? View.VISIBLE : View.GONE));
        activity.addSwitchRow(hollowSection, "镂空电池",
                "开启后电池内数字使用透明挖空显示。",
                SettingsStore.KEY_BATTERY_HOLLOW_ENABLED,
                SettingsStore.DEFAULT_BATTERY_HOLLOW_ENABLED,
                (buttonView, isChecked) -> hollowOptions.setVisibility(isChecked ? View.VISIBLE : View.GONE));
        LinearLayout.LayoutParams hollowOptionsLp = activity.matchWrapWithTop(10);
        hollowOptionsLp.leftMargin = activity.dp(12);
        hollowSection.addView(hollowOptions, hollowOptionsLp);
        LinearLayout.LayoutParams hollowSectionLp = activity.matchWrapWithTop(10);
        hollowSectionLp.leftMargin = activity.dp(12);
        content.addView(hollowSection, hollowSectionLp);
        activity.addDivider(content);
        int[] batteryTextFontOptions = BatteryTextFontHelper.getAvailableFontOptions(activity);
        activity.addChoiceRow(content, "电池数字字体",
                "会列出系统可用字体，也包含模块自带的 MiSansLatinVFNumber。",
                SettingsStore.KEY_BATTERY_TEXT_FONT,
                SettingsStore.DEFAULT_BATTERY_TEXT_FONT,
                batteryTextFontOptions,
                BatteryTextFontHelper.getFontLabels(batteryTextFontOptions));
        return activity.buildSectionCard(
                "电池样式",
                "状态栏电池绘制、数字样式和镂空细节都集中在这里。",
                content);
    }

    View createNotificationSettingsCard() {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);

        LinearLayout appIconOptions = buildNotificationAppIconOptions();
        appIconOptions.setVisibility(SettingsStore.readBoolean(
                activity.prefs(),
                SettingsStore.KEY_NOTIFICATION_APP_ICON_ENABLED,
                SettingsStore.DEFAULT_NOTIFICATION_APP_ICON_ENABLED) ? View.VISIBLE : View.GONE);
        activity.addSwitchRow(content, "通知使用应用图标",
                "开启后把第三方应用的状态栏通知图标改成应用自身图标，不再使用 Flyme 统一通知图标。",
                SettingsStore.KEY_NOTIFICATION_APP_ICON_ENABLED,
                SettingsStore.DEFAULT_NOTIFICATION_APP_ICON_ENABLED,
                (buttonView, isChecked) -> appIconOptions.setVisibility(isChecked ? View.VISIBLE : View.GONE));
        LinearLayout.LayoutParams optionsLp = activity.matchWrapWithTop(10);
        optionsLp.leftMargin = activity.dp(12);
        content.addView(appIconOptions, optionsLp);
        return activity.buildSectionCard(
                "通知图标",
                "这里只改第三方应用通知图标的来源、尺寸和内边距。",
                content);
    }

    View createSignalSettingsCard() {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);

        activity.addSwitchRow(content, "代码绘制信号图标",
                "关闭后恢复系统原来的移动信号和 Wi-Fi 图标，不再替换相关槽位和尺寸。",
                SettingsStore.KEY_SIGNAL_CODE_DRAW_ENABLED,
                SettingsStore.DEFAULT_SIGNAL_CODE_DRAW_ENABLED);
        activity.addDivider(content);
        activity.addSwitchRow(content, "显示 5G/5GA 标识",
                "关闭后只保留移动信号图标，不显示右侧移动网络类型标识。",
                SettingsStore.KEY_SIGNAL_MOBILE_TYPE_BADGE_ENABLED,
                SettingsStore.DEFAULT_SIGNAL_MOBILE_TYPE_BADGE_ENABLED);
        activity.addDivider(content);
        activity.addSwitchRow(content, "重绘 Wi-Fi 图标",
                "在信号总开关开启时，单独控制是否继续接管 Wi-Fi 图标。",
                SettingsStore.KEY_WIFI_CODE_DRAW_ENABLED,
                SettingsStore.DEFAULT_WIFI_CODE_DRAW_ENABLED);
        activity.addDivider(content);
        activity.addSwitchRow(content, "交换 Wi-Fi 与信号位置",
                "让 Wi-Fi 图标显示在电池和移动信号之间。",
                SettingsStore.KEY_SIGNAL_WIFI_SWAP_ENABLED,
                SettingsStore.DEFAULT_SIGNAL_WIFI_SWAP_ENABLED);
        activity.addDivider(content);
        activity.addProfileSectionHeader(content, "说明",
                "5G/5GA 标识开启时跟随系统真实网络状态或 Telephony 调试结果。");
        return activity.buildSectionCard(
                "信号与 Wi-Fi",
                "移动网络图标统一由模块接管，同时保留 Wi-Fi 的独立开关。",
                content);
    }

    View createConnectionRateSettingsCard() {
        return activity.buildSectionCard(
                "实时网速",
                "保留系统原采样，只在这里调节阈值显隐和确认次数。",
                buildConnectionRateThresholdPage());
    }

    View createTimeExpressionSettingsCard() {
        return activity.buildSectionCard(
                "时间表达式",
                "通过按钮组合表达式，并在当前页完成拖动排序和应用。",
                activity.clockExpressionEditor().buildPage());
    }

    View createTimeInteractionSettingsCard() {
        return activity.buildSectionCard(
                "时间交互",
                "控制左上角时钟点击后的详细时间弹窗，当前默认 8 秒自动收起。",
                buildTimeInteractionPage());
    }

    View createTimeTypographySettingsCard() {
        return activity.buildSectionCard(
                "时间字体",
                "集中控制状态栏时间、追加日期和锁屏运营商的字重与字号。",
                buildTimeTypographyPage());
    }

    View createMBackActionSettingsCard() {
        return activity.buildSectionCard(
                "MBack 长触动作",
                "只接管长按分支，保留单击和系统其他来源。",
                buildMBackActionPage());
    }

    View createWindowModeSideGestureSettingsCard() {
        return activity.buildSectionCard(
                "小窗侧边手势",
                "接管 Flyme 小窗的左右侧边触发手势，改为执行模块动作。",
                buildWindowModeSideGesturePage());
    }

    View createMBackNavigationSettingsCard() {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(buildMBackImmersivePage(), activity.matchWrap());
        activity.addDivider(content);
        content.addView(buildMBackHeightPage(), activity.matchWrapWithTop(8));
        return activity.buildSectionCard(
                "导航栏沉浸与高度",
                "透明背景、隐藏小白条、Inset 抬高和导航栏高度在同一页平铺展示。",
                content);
    }

    View createImeToolbarSettingsCard() {
        return activity.buildSectionCard(
                "IME 控制栏",
                "统一接管输入法控制栏，并保留图标缩放、透明度、抬高和按钮草稿应用逻辑。",
                activity.imeToolbarEditor().buildSettingsContent());
    }

    View createLauncherRecentsSettingsCard() {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        activity.addSwitchRow(content, "IOS 式堆叠后台",
                "Hook Flyme launcher 的 Recent，把原来的 PagedView 卡片压成重叠 stack carousel，并去掉原有自动横向推开和居中修正。",
                SettingsStore.KEY_LAUNCHER_IOS_STACK_RECENTS_ENABLED,
                SettingsStore.DEFAULT_LAUNCHER_IOS_STACK_RECENTS_ENABLED);
        activity.addDivider(content);
        activity.addSwitchRow(content, "堆叠后台 blur 效果",
                "关闭后保留 IOS 式堆叠后台布局，但不再模糊边缘堆叠卡片的截图和图标。",
                SettingsStore.KEY_LAUNCHER_IOS_STACK_RECENTS_BLUR_ENABLED,
                SettingsStore.DEFAULT_LAUNCHER_IOS_STACK_RECENTS_BLUR_ENABLED);
        activity.addDivider(content);
        activity.addSwitchRow(content, "显示清除全部按钮",
                "只在 IOS 式堆叠后台开启时生效。",
                SettingsStore.KEY_LAUNCHER_IOS_STACK_RECENTS_CLEAR_ALL_BUTTON_ENABLED,
                SettingsStore.DEFAULT_LAUNCHER_IOS_STACK_RECENTS_CLEAR_ALL_BUTTON_ENABLED);
        activity.addDivider(content);
        activity.addActionButtonRow(content, "重启系统桌面",
                "后台布局需要重启系统桌面后再看完整效果。",
                "重启", activity::restartLauncher);
        return activity.buildSectionCard(
                "系统桌面后台",
                "作用域是 com.meizu.flyme.launcher。保留原有 Quickstep 手势入口，只在原地改后台卡片布局。",
                content);
    }

    View createSystemAppearanceSettingsCard() {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        activity.addProfileSectionHeader(content, "系统通知",
                "修改 SystemUI 通知卡片的模糊背景前景色。");
        LinearLayout blurOnlyOptions = new LinearLayout(activity);
        Switch[] textColorSwitchHolder = new Switch[1];
        Switch blurOnlySwitch = activity.addSwitchRow(content, "仅保留系统模糊",
                "开启后忽略下面的通知背景颜色，移除通知背景，只保留系统动态模糊层。",
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
        Switch textColorSwitch = activity.addSwitchRow(blurOnlyOptions, "通知字体跟随状态栏",
                "通知文字跟随当前状态栏图标颜色，只在仅保留系统模糊时生效。",
                SettingsStore.KEY_NOTIFICATION_TEXT_FOLLOW_STATUS_BAR_ENABLED,
                SettingsStore.DEFAULT_NOTIFICATION_TEXT_FOLLOW_STATUS_BAR_ENABLED);
        textColorSwitchHolder[0] = textColorSwitch;
        textColorSwitch.setEnabled(blurOnlySwitch.isChecked());
        blurOnlyOptions.setAlpha(blurOnlySwitch.isChecked() ? 1f : 0.45f);
        content.addView(blurOnlyOptions, blurOnlyOptionsLp);
        activity.addDivider(content);
        activity.addTextSettingRow(content, "通知背景颜色",
                "填写 #AARRGGBB。留空跟随系统；可填 #1A000000；上方开关开启时此项不生效。",
                SettingsStore.KEY_NOTIFICATION_BACKGROUND_COLOR,
                SettingsStore.DEFAULT_NOTIFICATION_BACKGROUND_COLOR,
                "跟随系统",
                "#1A000000",
                true);
        activity.addDivider(content);
        activity.addActionButtonRow(content, "重启 SystemUI",
                "通知背景需要重启 SystemUI 后刷新。",
                "重启", activity::restartSystemUi);
        activity.addDivider(content);
        activity.addProfileSectionHeader(content, "系统桌面文件夹",
                "修改 Flyme 桌面文件夹图标的圆角背景颜色。");
        activity.addTextSettingRow(content, "文件夹圆角背景颜色",
                "填写 #AARRGGBB。留空跟随系统；原浅色约为 #73FFFFFF，纯透明为 #00000000。",
                SettingsStore.KEY_LAUNCHER_FOLDER_BG_COLOR,
                SettingsStore.DEFAULT_LAUNCHER_FOLDER_BG_COLOR,
                "跟随系统",
                "#73FFFFFF",
                true);
        activity.addDivider(content);
        activity.addActionButtonRow(content, "重启系统桌面",
                "桌面文件夹背景通常需要重启桌面后刷新。",
                "重启", activity::restartLauncher);
        return activity.buildSectionCard(
                "系统外观",
                "修改通知背景和 Flyme 桌面文件夹背景。",
                content);
    }

    View createAdvancedToolsCard() {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        activity.addActionButtonRow(content, "布局微调",
                "进入独立工具页，微调时钟、电池、信号、Wi-Fi 与输入法控制栏的位置。",
                "进入", activity::showPositionTuningPage);
        activity.addDivider(content);
        activity.addActionButtonRow(content, "Telephony 调试",
                "进入独立调试页，伪造插卡数量、默认数据卡、网络类型和信号等级。",
                "进入", activity::showTelephonyDebugPage);
        return activity.buildSectionCard(
                "高阶工具",
                "面向需要进一步验证布局或 Telephony 行为的场景。",
                content);
    }

    View createConfigManagementCard() {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        activity.addActionButtonRow(content, "导入配置",
                "从 JSON 文件恢复当前模块配置，会直接覆盖现有设置。",
                "导入", activity::startImportConfig);
        activity.addDivider(content);
        activity.addActionButtonRow(content, "导出配置",
                "把当前可备份的设置项导出到 JSON，便于备份或跨设备迁移。",
                "导出", activity::startExportConfig);
        activity.addDivider(content);
        activity.addActionButtonRow(content, "恢复默认",
                "清空当前 SharedPreferences，并重新按默认值初始化界面。",
                "恢复", activity::resetAllSettings);
        return activity.buildSectionCard(
                "配置管理",
                "原来藏在悬浮菜单里的导入、导出和恢复默认，现在都放到主页面里。",
                content);
    }

    View createPerformanceDebugCard() {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        activity.addSwitchRow(content, "启用 WIFI 性能打点",
                "打开后会给 Wi-Fi 更新链路输出详细耗时日志，方便在 logcat 里分析刷新开销。",
                SettingsStore.KEY_WIFI_PERF_LOGGING_ENABLED,
                SettingsStore.DEFAULT_WIFI_PERF_LOGGING_ENABLED);
        activity.addDivider(content);
        activity.addSwitchRow(content, "启用后台堆叠性能打点",
                "打开后输出 IOS 式堆叠后台的布局、状态准备、动画帧、手势和跳过次数日志。",
                SettingsStore.KEY_LAUNCHER_RECENTS_PERF_LOGGING_ENABLED,
                SettingsStore.DEFAULT_LAUNCHER_RECENTS_PERF_LOGGING_ENABLED);
        activity.addDivider(content);
        activity.addSwitchRow(content, "IOS 堆叠后台日志",
                "打开后输出滑动触发的触摸、分页释放、滑动删除、布局和动画流程。",
                SettingsStore.KEY_LAUNCHER_RECENTS_FLOW_LOGGING_ENABLED,
                SettingsStore.DEFAULT_LAUNCHER_RECENTS_FLOW_LOGGING_ENABLED);
        return activity.buildSectionCard(
                "性能调试",
                "只保留和现有模块实现直接相关的性能打点开关。",
                content);
    }

    View createOneMindPerfControlCard() {
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
        activity.addDivider(content);
        activity.addActionButtonRow(content, "重启 OneMind/PPS",
                "开关变更后重启 PPS 让进程重新加载模块。",
                "重启", activity::restartOneMindPps);
        return activity.buildSectionCard(
                "OneMind 性能调节",
                "仅处理 PPS 性能下发入口，不处理热控。",
                content);
    }

    View createPositionTuningSettingsCard() {
        activity.positionTuningSliderBindings().clear();

        LinearLayout card = activity.card(activity.surfaceColor(), activity.strokeColor(), 28);
        activity.addProfileSectionHeader(card, "时钟与通知图区",
                "改的是状态栏 clock View 的右侧 padding，会直接影响时间和右侧通知图标区之间的间距。");
        activity.addPositionOffsetSliderRow(card, "时钟右边距",
                "基于系统默认间距做增减。系统原本的右侧边距为 2dp。正数增大间距，负数减小间距。",
                SettingsStore.KEY_CLOCK_RIGHT_PADDING_OFFSET_DP,
                SettingsStore.DEFAULT_CLOCK_RIGHT_PADDING_OFFSET_DP * 10);

        activity.addDivider(card);
        activity.addProfileSectionHeader(card, "电池",
                "下面 3 项只在模块接管电池绘制后生效。");
        activity.addPositionOffsetSliderRow(card, "电池图标",
                "整体电池轮廓的 Y 轴位置。默认 0dp。",
                SettingsStore.KEY_BATTERY_ICON_Y_OFFSET_DP,
                SettingsStore.DEFAULT_BATTERY_ICON_Y_OFFSET_DP * 10);
        activity.addDivider(card);
        activity.addPositionOffsetSliderRow(card, "电池内数字数显",
                "只改电池内部数字的基线高度。默认 0dp。",
                SettingsStore.KEY_BATTERY_TEXT_Y_OFFSET_DP,
                SettingsStore.DEFAULT_BATTERY_TEXT_Y_OFFSET_DP * 10);
        activity.addDivider(card);
        activity.addPositionOffsetSliderRow(card, "闪电图标",
                "只改充电 / 快充闪电图标的 Y 轴位置。默认 0dp。",
                SettingsStore.KEY_BATTERY_BOLT_Y_OFFSET_DP,
                SettingsStore.DEFAULT_BATTERY_BOLT_Y_OFFSET_DP * 10);

        activity.addDivider(card);
        activity.addProfileSectionHeader(card, "移动网络",
                "这些项只影响模块自绘的移动信号和 5G/5GA 标识。");
        activity.addPositionOffsetSliderRow(card, "单层信号图标",
                "单卡场景下信号柱的 Y 轴位置。默认 0dp。",
                SettingsStore.KEY_SIGNAL_SINGLE_Y_OFFSET_DP,
                SettingsStore.DEFAULT_SIGNAL_SINGLE_Y_OFFSET_DP * 10);
        activity.addDivider(card);
        activity.addPositionOffsetSliderRow(card, "5G / 5GA 标识",
                "只改 5G / 5GA 文本标识的 Y 轴位置。默认 0dp。",
                SettingsStore.KEY_SIGNAL_BADGE_Y_OFFSET_DP,
                SettingsStore.DEFAULT_SIGNAL_BADGE_Y_OFFSET_DP * 10);
        activity.addDivider(card);
        activity.addPositionOffsetSliderRow(card, "双层信号图标",
                "双卡合一场景下整组信号图形的 Y 轴位置。默认 0dp。",
                SettingsStore.KEY_SIGNAL_DUAL_Y_OFFSET_DP,
                SettingsStore.DEFAULT_SIGNAL_DUAL_Y_OFFSET_DP * 10);

        activity.addDivider(card);
        activity.addProfileSectionHeader(card, "Wi-Fi",
                "只改模块自绘的 Wi-Fi 图标。默认 0dp。");
        activity.addPositionOffsetSliderRow(card, "Wi-Fi 图标",
                "Wi-Fi 图标整体的 Y 轴位置。默认 0dp。",
                SettingsStore.KEY_WIFI_Y_OFFSET_DP,
                SettingsStore.DEFAULT_WIFI_Y_OFFSET_DP * 10);

        activity.addDivider(card);
        activity.addProfileSectionHeader(card, "输入法控制栏",
                "这里单独保留输入法控制栏整体抬高的细调项。");
        activity.addPositionOffsetSliderRow(card, "输入法控制栏抬高",
                "正数向上、负数向下；滑块按 1dp 粗调，点右侧数值可输入 0.1dp。",
                SettingsStore.KEY_IME_CONTROL_BAR_Y_OFFSET_DP,
                SettingsStore.DEFAULT_IME_CONTROL_BAR_Y_OFFSET_DP * 10);

        activity.addDivider(card);
        activity.addActionButtonRow(card, "应用当前微调",
                "把这个页面里的待应用微调值一次性写入配置，并通知当前状态栏刷新。",
                "应用", activity::applyAllPositionOffsets);
        activity.addDivider(card);
        activity.addActionButtonRow(card, "全部归零",
                "先把这个页面里的待应用微调值都改成 0.0dp；改完后再点上面的应用写入状态栏。",
                "归零", activity::resetAllPositionOffsets);
        return card;
    }

    View createTelephonyDebugSettingsCard() {
        LinearLayout card = activity.card(activity.surfaceColor(), activity.strokeColor(), 28);
        activity.addProfileSectionHeader(card, "调试开关",
                "打开后，远端偏好会立即同步到 SystemUI。你可以先设好两张测试卡的状态，再切换插卡数量和默认数据卡。");
        activity.addSwitchRow(card, "启用 Telephony 伪造",
                "关闭后恢复真实 Telephony 结果，但下面保存的调试预设会保留。",
                SettingsStore.KEY_TELEPHONY_DEBUG_ENABLED,
                SettingsStore.DEFAULT_TELEPHONY_DEBUG_ENABLED);
        activity.addDivider(card);
        activity.addChoiceRow(card, "模拟插卡数量",
                "用于测试 0 卡、单卡和双卡时你的自绘信号图标是否按预期切换布局和可见性。",
                SettingsStore.KEY_TELEPHONY_DEBUG_SIM_COUNT,
                SettingsStore.DEFAULT_TELEPHONY_DEBUG_SIM_COUNT,
                new int[]{0, 1, 2},
                new String[]{"0 张", "1 张", "2 张"});
        activity.addDivider(card);
        activity.addChoiceRow(card, "默认上网卡",
                "双卡场景下，移动网络类型和 5G 标识会跟随这里选择的那张卡。",
                SettingsStore.KEY_TELEPHONY_DEBUG_DEFAULT_DATA_SLOT,
                SettingsStore.DEFAULT_TELEPHONY_DEBUG_DEFAULT_DATA_SLOT,
                new int[]{
                        SettingsStore.TELEPHONY_DEBUG_DEFAULT_DATA_SLOT_NONE,
                        SettingsStore.TELEPHONY_DEBUG_DEFAULT_DATA_SLOT_CARD1,
                        SettingsStore.TELEPHONY_DEBUG_DEFAULT_DATA_SLOT_CARD2
                },
                new String[]{"无", "卡 1", "卡 2"});

        activity.addDivider(card);
        addTelephonyDebugSlotSection(card,
                "卡 1",
                "第一张测试卡。单卡场景默认看它；双卡合并图标时，上层柱读取它的信号等级。",
                SettingsStore.KEY_TELEPHONY_DEBUG_SLOT1_NETWORK_PROFILE,
                SettingsStore.DEFAULT_TELEPHONY_DEBUG_SLOT1_NETWORK_PROFILE,
                SettingsStore.KEY_TELEPHONY_DEBUG_SLOT1_SIGNAL_LEVEL,
                SettingsStore.DEFAULT_TELEPHONY_DEBUG_SLOT1_SIGNAL_LEVEL);

        activity.addDivider(card);
        addTelephonyDebugSlotSection(card,
                "卡 2",
                "第二张测试卡。只有插卡数量切到 2 张时才会参与模拟；双卡合并图标时，下层圆点读取它的信号等级。",
                SettingsStore.KEY_TELEPHONY_DEBUG_SLOT2_NETWORK_PROFILE,
                SettingsStore.DEFAULT_TELEPHONY_DEBUG_SLOT2_NETWORK_PROFILE,
                SettingsStore.KEY_TELEPHONY_DEBUG_SLOT2_SIGNAL_LEVEL,
                SettingsStore.DEFAULT_TELEPHONY_DEBUG_SLOT2_SIGNAL_LEVEL);

        activity.addDivider(card);
        activity.addActionButtonRow(card, "恢复真实系统",
                "只关闭 Telephony 伪造，不清空你刚才配好的两张测试卡参数。",
                "恢复", activity::disableTelephonyDebug);
        return card;
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
        return card;
    }

    private View buildStatusBarIconScaleCard() {
        LinearLayout card = activity.card(activity.surfaceColor(), 24);

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(activity);
        title.setText("状态栏图标大小");
        title.setTextColor(activity.textColor());
        title.setTextSize(18);
        header.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        activity.addHelpButton(header, "状态栏图标大小",
                "统一控制右上角系统状态图标，以及代码绘制开启后的电池图标和信号图标。通知图标、隐私权限标识和隐私圆点不在这里面。");
        card.addView(header, activity.matchWrap());

        activity.addDivider(card);
        activity.addApplySliderRow(card, "全部状态栏图标大小",
                "默认 100%。统一调右上角系统状态图标，还有代码绘制的电池和信号图标。",
                SettingsStore.KEY_STATUS_BAR_ICON_SCALE_PERCENT,
                SettingsStore.DEFAULT_STATUS_BAR_ICON_SCALE_PERCENT, 50, 200, "%");
        activity.addDivider(card);
        activity.addApplySliderRow(card, "电池内部数字大小",
                "只改电池图标内部的电量数字。默认 100%。",
                SettingsStore.KEY_BATTERY_INNER_TEXT_SCALE_PERCENT,
                SettingsStore.DEFAULT_BATTERY_INNER_TEXT_SCALE_PERCENT, 50, 200, "%");
        return card;
    }

    private LinearLayout buildMBackActionPage() {
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);

        activity.addProfileSectionHeader(page, "长触动作",
                "只接管 mBack 长触分支，保留单击和系统其他来源。");
        activity.addSwitchRow(page, "接管长触 mBack",
                "拦截 Flyme SystemUI 里原本唤醒 AICY 的 mBack/Home 长触入口，改为执行这里配置的长触动作。",
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

        activity.addProfileSectionHeader(page, "侧边手势",
                "只接管 Flyme 小窗侧边触发回调。关闭后恢复原来的小窗面板。");
        activity.addSwitchRow(page, "预热原生小窗面板",
                "提前准备 Flyme 原生小窗选择面板，降低首次触发卡顿。",
                SettingsStore.KEY_WINDOWMODE_SIDE_GESTURE_PREWARM_ENABLED,
                SettingsStore.DEFAULT_WINDOWMODE_SIDE_GESTURE_PREWARM_ENABLED);
        activity.addDivider(page);
        activity.addSwitchRow(page, "接管侧边小窗手势",
                "拦截屏幕左右侧边的小窗触发手势，改为执行这里配置的动作。",
                SettingsStore.KEY_WINDOWMODE_SIDE_GESTURE_ENABLED,
                SettingsStore.DEFAULT_WINDOWMODE_SIDE_GESTURE_ENABLED);
        activity.addDivider(page);
        activity.addChoiceRow(page, "触发动作",
                "可选发送 URL / Intent、底部时间弹窗，或者后台应用星图。",
                SettingsStore.KEY_WINDOWMODE_SIDE_GESTURE_ACTION,
                SettingsStore.DEFAULT_WINDOWMODE_SIDE_GESTURE_ACTION,
                new int[]{
                        SettingsStore.MBACK_LONG_TOUCH_ACTION_INTENT_URI,
                        SettingsStore.MBACK_LONG_TOUCH_ACTION_CLOCK_POPUP,
                        SettingsStore.MBACK_LONG_TOUCH_ACTION_STAR_APPS
                },
                new String[]{"URL / Intent", "底部时间弹窗", "后台应用星图"});
        activity.addDivider(page);
        activity.addTextSettingRow(page, "目标 URL / Intent URI",
                "只在“URL / Intent”模式下生效。支持 https://、自定义 scheme 和 intent:// URI。留空则回退 Flyme 小窗面板。",
                SettingsStore.KEY_WINDOWMODE_SIDE_GESTURE_INTENT_URI,
                SettingsStore.DEFAULT_WINDOWMODE_SIDE_GESTURE_INTENT_URI,
                "未设置");
        activity.addDivider(page);
        activity.addActionButtonRow(page, "测试 URL / Intent",
                "只测试当前填写的 URL / Intent URI，不测试底部时间弹窗模式。",
                "立即测试", activity::testLaunchWindowModeSideGestureIntent);
        return page;
    }

    private LinearLayout buildMBackImmersivePage() {
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);

        activity.addProfileSectionHeader(page, "沉浸 / Inset",
                "这里主要调透明背景和 mBack 背景抬高。透明只改背景层；这里的 inset 指把 mBack 背景往上抬，同时会影响应用对底部区域的判断。");
        activity.addSwitchRow(page, "mBack 导航栏透明",
                "把 mBack 所在导航栏背景压成透明，只动导航栏背景层，不改 mBack 本体绘制。",
                SettingsStore.KEY_MBACK_NAV_BAR_TRANSPARENT,
                SettingsStore.DEFAULT_MBACK_NAV_BAR_TRANSPARENT);
        activity.addDivider(page);
        activity.addSwitchRow(page, "隐藏小白条",
                "只隐藏 mBack 自己画出来的那条胶囊，不直接改长触逻辑和 inset。适合配合透明背景和背景抬高一起调。",
                SettingsStore.KEY_MBACK_HIDE_PILL,
                SettingsStore.DEFAULT_MBACK_HIDE_PILL);
        activity.addDivider(page);
        activity.addApplyInsetSliderRow(page, "mBack inset 大小",
                "这里的 inset 指 mBack 背景抬高。-1 表示保持系统默认，0 表示不额外抬高，其他数值按 dp 处理；同时也会影响应用感知到的底部区域。",
                SettingsStore.KEY_MBACK_INSET_SIZE,
                SettingsStore.DEFAULT_MBACK_INSET_SIZE, -1, 80);
        return page;
    }

    private LinearLayout buildMBackHeightPage() {
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);

        activity.addProfileSectionHeader(page, "导航栏高度",
                "这个页直接压 mBack 导航栏窗口本身的高度，比单纯透明更能减少底部透明可触区域对应用按钮的遮挡。");
        activity.addApplyInsetSliderRow(page, "mBack 导航栏高度",
                "控制 mBack 导航栏窗口本身的高度。-1 表示保持系统默认，数值越小，底部透明可触区域越矮。这个项更直接影响应用底部按钮是否容易被挡住。",
                SettingsStore.KEY_MBACK_NAV_BAR_HEIGHT,
                SettingsStore.DEFAULT_MBACK_NAV_BAR_HEIGHT, -1, 80);
        return page;
    }

    private LinearLayout buildConnectionRateThresholdPage() {
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);

        activity.addProfileSectionHeader(page, "阈值显隐",
                "显示阈值和隐藏阈值分开，连续采样确认后再切换，继续用 GONE 但尽量避免频繁抖动。");
        activity.addSwitchRow(page, "启用阈值显隐",
                "高于显示阈值时显示，低于隐藏阈值时隐藏。只改显示，不改采样。",
                SettingsStore.KEY_CONNECTION_RATE_AUTO_VISIBILITY_ENABLED,
                SettingsStore.DEFAULT_CONNECTION_RATE_AUTO_VISIBILITY_ENABLED);
        activity.addDivider(page);
        activity.addSliderRow(page, "显示阈值",
                "连续达到这个速度后才显示，单位 KB/s",
                SettingsStore.KEY_CONNECTION_RATE_SHOW_THRESHOLD_KB,
                SettingsStore.DEFAULT_CONNECTION_RATE_SHOW_THRESHOLD_KB, 0, 1024, "KB/s");
        activity.addDivider(page);
        activity.addSliderRow(page, "隐藏阈值",
                "低于这个速度后才隐藏，单位 KB/s",
                SettingsStore.KEY_CONNECTION_RATE_HIDE_THRESHOLD_KB,
                SettingsStore.DEFAULT_CONNECTION_RATE_HIDE_THRESHOLD_KB, 0, 1024, "KB/s");
        activity.addDivider(page);
        activity.addSliderRow(page, "显示确认次数",
                "连续多少次达到显示阈值才真正显示",
                SettingsStore.KEY_CONNECTION_RATE_SHOW_SAMPLE_COUNT,
                SettingsStore.DEFAULT_CONNECTION_RATE_SHOW_SAMPLE_COUNT, 1, 5, "次");
        activity.addDivider(page);
        activity.addSliderRow(page, "隐藏确认次数",
                "连续多少次低于隐藏阈值才真正隐藏",
                SettingsStore.KEY_CONNECTION_RATE_HIDE_SAMPLE_COUNT,
                SettingsStore.DEFAULT_CONNECTION_RATE_HIDE_SAMPLE_COUNT, 1, 5, "次");
        return page;
    }

    private LinearLayout buildTimeTypographyPage() {
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);

        activity.addProfileSectionHeader(page, "字重 / 字号",
                "这里集中控制状态栏时间、右侧追加日期以及锁屏运营商相关文字的字重和字号。");
        activity.addSwitchRow(page, "时间加粗",
                "对状态栏时间以及其右侧追加的星期/日期应用字重",
                SettingsStore.KEY_CLOCK_BOLD_ENABLED, SettingsStore.DEFAULT_CLOCK_BOLD_ENABLED);
        activity.addDivider(page);
        activity.addApplySliderRow(page, "时间/日期粗细",
                "只对状态栏时间文字生效，范围 100-900",
                SettingsStore.KEY_CLOCK_FONT_WEIGHT, SettingsStore.DEFAULT_CLOCK_FONT_WEIGHT, 100, 900, "");
        activity.addDivider(page);
        activity.addApplySliderRow(page, "时间和锁屏运营商字体大小",
                "同时控制左上角时间、锁屏界面运营商，以及网速显示文字大小。默认 100%。",
                SettingsStore.KEY_CLOCK_AND_CARRIER_TEXT_SIZE_PERCENT,
                SettingsStore.DEFAULT_CLOCK_AND_CARRIER_TEXT_SIZE_PERCENT, 50, 200, "%");
        return page;
    }

    private LinearLayout buildTimeInteractionPage() {
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);

        activity.addProfileSectionHeader(page, "主状态栏时钟",
                "只作用于左上角主状态栏时钟，不影响锁屏时钟和其他同类时间控件。");
        activity.addSwitchRow(page, "点击时钟显示详细时间",
                "点击左上角时钟后先显示时间和日期；窗口内下拉再展开内存、电池和最近任务，上拉直接收回。",
                SettingsStore.KEY_CLOCK_DETAIL_POPUP_ENABLED,
                SettingsStore.DEFAULT_CLOCK_DETAIL_POPUP_ENABLED);
        activity.addDivider(page);
        activity.addSwitchRow(page, "显示农历日期",
                "控制详细时间弹窗中的农历日期显示",
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
        activity.addSwitchRow(page, "显示固定快捷启动图标",
                "在详细区里新增最多 5 个图标快捷入口；候选项来自 Aicy纵览里可扫描到的快捷启动。",
                SettingsStore.KEY_CLOCK_DETAIL_ACTION_GRID_ENABLED,
                SettingsStore.DEFAULT_CLOCK_DETAIL_ACTION_GRID_ENABLED,
                (buttonView, isChecked) -> actionGridEditorSection.setVisibility(
                        isChecked ? View.VISIBLE : View.GONE));
        activity.addDivider(actionGridEditorSection);
        activity.addActionButtonRow(actionGridEditorSection, "编辑图标入口顺序",
                "优先使用已缓存候选；可在编辑器里扫描/刷新 Aicy纵览中的快捷启动列表。最多选择 5 个，长按已选图标拖动排序，点应用后保存。",
                "编辑", activity::showClockDetailActionGridEditor);
        page.addView(actionGridEditorSection, activity.matchWrap());
        return page;
    }

    private LinearLayout buildBatteryHollowOptions() {
        LinearLayout card = activity.card(activity.surfaceSoftColor(), activity.strokeColor(), 22);
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
        activity.addProfileSectionHeader(root, titleText, subtitleText);
        activity.addChoiceRow(root, "网络类型",
                "改的是 Telephony 读到的网络制式。4G 会隐藏 5G 标识，5G / 5G CA / 5GA / 5G+ 会分别走不同的 5G 分支。",
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
        activity.addDivider(root);
        activity.addChoiceRow(root, "信号强度",
                "这里填的是标准 0 到 4 级。代码绘制信号图标会直接跟着这个等级变化。",
                signalKey,
                defaultSignalValue,
                new int[]{0, 1, 2, 3, 4},
                new String[]{"0 格", "1 格", "2 格", "3 格", "4 格"});
    }
}
