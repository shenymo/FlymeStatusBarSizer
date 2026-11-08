package com.example.flymestatusbarsizer.ui;

import com.example.flymestatusbarsizer.MainActivity;
import com.example.flymestatusbarsizer.config.RemoteSettingsSync;
import com.example.flymestatusbarsizer.config.SettingsStore;
import com.example.flymestatusbarsizer.feature.ime.ImeToolbarSpec;
import com.example.flymestatusbarsizer.feature.ime.VerificationCodeClient;
import com.example.flymestatusbarsizer.feature.ime.VerificationCodeProvider;

import android.content.ComponentName;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Set;

public final class VerificationCodeDiagnosticsPageController {
    private static final String PHONE_PACKAGE = "com.android.phone";

    private VerificationCodeDiagnosticsPageController() {
    }

    public static void bind(MainActivity activity, LinearLayout root) {
        LinearLayout overview = new LinearLayout(activity);
        overview.setOrientation(LinearLayout.VERTICAL);
        TextView status = text(activity, "正在读取诊断状态…", 14, activity.subtextColor());
        overview.addView(status, PageViewUtils.matchWrap());
        root.addView(activity.buildSectionCard("当前状态", null, overview, true),
                PageViewUtils.matchWrap());
        activity.addSwitchRow(root, "验证码诊断记录",
                "关闭后停止采集新的诊断记录，不影响验证码自动读取。",
                SettingsStore.KEY_IME_VERIFICATION_CODE_DIAGNOSTICS_ENABLED,
                SettingsStore.DEFAULT_IME_VERIFICATION_CODE_DIAGNOSTICS_ENABLED);
        LinearLayout eventList = new LinearLayout(activity);
        eventList.setOrientation(LinearLayout.VERTICAL);

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        TextView refresh = activity.filledButton("刷新", activity.primaryColor(), android.graphics.Color.WHITE);
        TextView clear = activity.filledButton("清空记录", activity.surfaceSoftColor(), activity.textColor());
        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        activity.setTapClickListener(refresh, v -> refresh(activity, status, eventList));
        activity.setTapClickListener(clear, v -> {
            VerificationCodeClient.clearDiagnostics(activity);
            refresh(activity, status, eventList);
        });
        actions.addView(refresh, actionLp);
        LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        clearLp.leftMargin = activity.dp(8);
        actions.addView(clear, clearLp);
        root.addView(actions, PageViewUtils.matchWrapWithTop(activity, 8));

        LinearLayout timeline = new LinearLayout(activity);
        timeline.setOrientation(LinearLayout.VERTICAL);
        timeline.addView(eventList, PageViewUtils.matchWrap());
        root.addView(activity.buildSectionCard("最近诊断记录", null, timeline, true),
                PageViewUtils.matchWrapWithTop(activity, 8));
        refresh(activity, status, eventList);
    }

    private static void refresh(MainActivity activity, TextView status, LinearLayout eventList) {
        status.setText("正在读取诊断状态…");
        new Thread(() -> {
            Set<String> scope = RemoteSettingsSync.readEnabledScope();
            ArrayList<Bundle> events = VerificationCodeClient.readDiagnostics(activity);
            String imeSetting = Settings.Secure.getString(
                    activity.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
            ComponentName ime = ComponentName.unflattenFromString(imeSetting);
            String imePackage = ime == null ? null : ime.getPackageName();
            boolean enabled = activity.prefs().getBoolean("enabled", true);
            String buttonSlots = activity.readStringSetting(
                    SettingsStore.KEY_IME_CONTROL_BAR_BUTTON_SLOTS,
                    SettingsStore.DEFAULT_IME_CONTROL_BAR_BUTTON_SLOTS);
            String textSlots = activity.readStringSetting(
                    SettingsStore.KEY_IME_CONTROL_BAR_TEXT_SLOTS,
                    SettingsStore.DEFAULT_IME_CONTROL_BAR_TEXT_SLOTS);
            boolean hasButton = ImeToolbarSpec.containsVerificationCode(buttonSlots, textSlots);
            boolean visibleButton = ImeToolbarSpec.hasVisibleVerificationCode(
                    buttonSlots, textSlots,
                    activity.prefs().getBoolean(
                            SettingsStore.KEY_IME_CONTROL_BAR_EXPANSION_ENABLED,
                            SettingsStore.DEFAULT_IME_CONTROL_BAR_EXPANSION_ENABLED));
            activity.runOnUiThread(() -> {
                String phoneScope = scope == null ? "无法读取"
                        : scope.contains(PHONE_PACKAGE) ? "已启用" : "未启用";
                String imeScope = scope == null ? "无法读取"
                        : imePackage == null ? "无法识别当前输入法"
                        : scope.contains(imePackage) ? "已启用" : "未启用";
                boolean phoneHookObserved = false;
                if (events != null) {
                    for (Bundle event : events) {
                        if ("短信类型检查".equals(
                                event.getString(VerificationCodeProvider.KEY_STAGE))) {
                            phoneHookObserved = true;
                            break;
                        }
                    }
                }
                status.setText("模块功能：" + (enabled ? "已启用" : "未启用")
                        + "\n验证码按钮：" + (visibleButton ? "已加入工具栏"
                                : hasButton ? "已放入文字栏，但扩展栏未开启" : "未加入工具栏")
                        + "\n电话服务作用域：" + phoneScope
                        + "\n当前输入法作用域：" + imeScope
                        + "\n电话短信 Hook：" + (phoneHookObserved ? "已观察到运行" : "尚无运行记录")
                        + "\n诊断服务：" + (events == null ? "无法连接" : "已连接")
                        + "\n作用域状态需在 LSPosed 服务可连接时读取。");
                renderEvents(activity, eventList, events);
            });
        }, "verification-code-diagnostics").start();
    }

    private static void renderEvents(MainActivity activity, LinearLayout parent,
            ArrayList<Bundle> events) {
        parent.removeAllViews();
        if (events == null || events.isEmpty()) {
            TextView empty = text(activity,
                    events == null ? "无法连接诊断服务" : "尚无诊断记录",
                    14, activity.subtextColor());
            empty.setPadding(activity.dp(16), activity.dp(12), activity.dp(16), activity.dp(12));
            parent.addView(empty, PageViewUtils.matchWrap());
            return;
        }
        SimpleDateFormat format = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA);
        for (int i = events.size() - 1; i >= 0; i--) {
            Bundle event = events.get(i);
            boolean success = event.getBoolean(VerificationCodeProvider.KEY_SUCCESS);
            String stage = event.getString(VerificationCodeProvider.KEY_STAGE, "未知阶段");
            String reason = event.getString(VerificationCodeProvider.KEY_REASON, "");
            long time = event.getLong(VerificationCodeProvider.KEY_TIME);
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(activity.dp(16), activity.dp(10), activity.dp(16), activity.dp(10));
            TextView heading = text(activity,
                    format.format(new Date(time)) + "  " + stage + "  " + (success ? "通过" : "未通过"),
                    14, success ? activity.textColor() : 0xFFB3261E);
            heading.setTypeface(android.graphics.Typeface.create(
                    "sans-serif-medium", android.graphics.Typeface.NORMAL));
            row.addView(heading, PageViewUtils.matchWrap());
            TextView detail = text(activity, reason, 13, activity.subtextColor());
            detail.setPadding(0, activity.dp(4), 0, 0);
            row.addView(detail, PageViewUtils.matchWrap());
            parent.addView(row, PageViewUtils.matchWrap());
            if (i > 0) {
                activity.addDivider(parent);
            }
        }
    }

    private static TextView text(MainActivity activity, String value, int size, int color) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }
}
