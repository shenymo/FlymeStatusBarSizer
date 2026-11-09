package com.example.flymestatusbarsizer.ui;

import com.example.flymestatusbarsizer.MainActivity;
import com.example.flymestatusbarsizer.config.RemoteSettingsSync;
import com.example.flymestatusbarsizer.config.SettingsStore;

import android.content.ComponentName;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Set;

public final class VerificationCodeDiagnosticsPageController {
    private static final String PHONE_PACKAGE = "com.android.phone";

    private VerificationCodeDiagnosticsPageController() {
    }

    public static void bind(MainActivity activity, LinearLayout root) {
        LinearLayout overview = new LinearLayout(activity);
        overview.setOrientation(LinearLayout.VERTICAL);
        TextView status = text(activity, "正在读取状态…", 14, activity.subtextColor());
        overview.addView(status, PageViewUtils.matchWrap());
        root.addView(activity.buildSectionCard("当前状态", null, overview, true),
                PageViewUtils.matchWrap());
        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        TextView refresh = activity.filledButton("刷新", activity.primaryColor(), android.graphics.Color.WHITE);
        TextView info = activity.filledButton("说明", activity.surfaceSoftColor(), activity.textColor());
        activity.setTapClickListener(refresh, v -> refresh(activity, status));
        activity.setTapClickListener(info, v -> status.setText(
                "验证码通过系统剪贴板传递，不启动模块进程。\n"
                        + "收到验证码短信会覆盖当前剪贴板内容。"));
        actions.addView(refresh, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        infoLp.leftMargin = activity.dp(8);
        actions.addView(info, infoLp);
        root.addView(actions, PageViewUtils.matchWrapWithTop(activity, 8));
        refresh(activity, status);
    }

    private static void refresh(MainActivity activity, TextView status) {
        status.setText("正在读取状态…");
        new Thread(() -> {
            Set<String> scope = RemoteSettingsSync.readEnabledScope();
            String imeSetting = Settings.Secure.getString(activity.getContentResolver(),
                    Settings.Secure.DEFAULT_INPUT_METHOD);
            ComponentName ime = ComponentName.unflattenFromString(imeSetting);
            String imePackage = ime == null ? null : ime.getPackageName();
            boolean enabled = activity.prefs().getBoolean("enabled", true);
            activity.runOnUiThread(() -> {
                String phoneScope = scope == null ? "无法读取"
                        : scope.contains(PHONE_PACKAGE) ? "已启用" : "未启用";
                String imeScope = scope == null ? "无法读取"
                        : imePackage == null ? "无法识别当前输入法"
                        : scope.contains(imePackage) ? "已启用" : "未启用";
                status.setText("模块功能：" + (enabled ? "已启用" : "未启用")
                        + "\n验证码功能：自动复制到系统剪贴板"
                        + "\n电话服务作用域：" + phoneScope
                        + "\n当前输入法作用域：" + imeScope
                        + "\n验证码传递：系统剪贴板\n不依赖模块后台进程。");
            });
        }, "verification-code-status").start();
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
