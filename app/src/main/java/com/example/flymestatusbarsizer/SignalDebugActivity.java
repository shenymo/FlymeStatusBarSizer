package com.example.flymestatusbarsizer;

import android.app.Activity;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

public class SignalDebugActivity extends Activity {
    private SharedPreferences prefs;
    private TextView runtimeDebugView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = SettingsStore.prefs(this);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.rgb(245, 245, 245));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(28));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("信号格调试");
        title.setTextColor(Color.rgb(32, 33, 36));
        title.setTextSize(24);
        root.addView(title, matchWrap());

        TextView summary = new TextView(this);
        summary.setText("给 SystemUI 进程提供假的移动信号等级，用来检查桌面、锁屏和控制中心的 iOS 信号格是否通过系统信号链路跟随变化。");
        summary.setTextColor(Color.rgb(95, 99, 104));
        summary.setTextSize(14);
        summary.setPadding(0, dp(6), 0, dp(14));
        root.addView(summary, matchWrap());

        addSwitch(root, "启用假信号等级", "开启后只 hook SystemUI 里的 SignalStrength / CellSignalStrength 等级读取，不直接给模块写入等级。",
                SettingsStore.KEY_IOS_SIGNAL_DEBUG_ENABLED,
                SettingsStore.DEFAULT_IOS_SIGNAL_DEBUG_ENABLED);
        addSwitch(root, "卡 1 参与测试", "关闭时隐藏卡 1；只开卡 2 时按单卡信号格显示。",
                SettingsStore.KEY_IOS_SIGNAL_DEBUG_SIM1_ENABLED,
                SettingsStore.DEFAULT_IOS_SIGNAL_DEBUG_SIM1_ENABLED);
        addSlider(root, "卡 1 假信号", "0 到 4 格，通过 SystemUI 的信号图标状态链路刷新，再验证 iOS 信号格是否变化。",
                SettingsStore.KEY_IOS_SIGNAL_DEBUG_SIM1_LEVEL,
                SettingsStore.DEFAULT_IOS_SIGNAL_DEBUG_SIM1_LEVEL, 0, 4, "格");
        addSwitch(root, "卡 2 参与测试", "双卡合一时卡 2 会显示在下方四个小点里。",
                SettingsStore.KEY_IOS_SIGNAL_DEBUG_SIM2_ENABLED,
                SettingsStore.DEFAULT_IOS_SIGNAL_DEBUG_SIM2_ENABLED);
        addSlider(root, "卡 2 假信号", "0 到 4 格，通过 SystemUI 的信号图标状态链路刷新，再验证 iOS 信号格是否变化。",
                SettingsStore.KEY_IOS_SIGNAL_DEBUG_SIM2_LEVEL,
                SettingsStore.DEFAULT_IOS_SIGNAL_DEBUG_SIM2_LEVEL, 0, 4, "格");

        runtimeDebugView = itemSubtitle("");
        runtimeDebugView.setTextColor(Color.rgb(32, 33, 36));
        LinearLayout runtimeCard = card();
        runtimeCard.addView(itemTitle("当前 Hook 状态"), matchWrap());
        runtimeCard.addView(runtimeDebugView, matchWrapWithTop(8));
        root.addView(runtimeCard, matchWrapWithTop(10));

        TextView refresh = button("刷新 Hook 状态");
        refresh.setOnClickListener(v -> requestRuntimeDebugRefresh());
        root.addView(refresh, matchWrapWithTop(10));

        TextView reset = button("关闭并恢复真实信号");
        reset.setOnClickListener(v -> {
            prefs.edit()
                    .remove(SettingsStore.KEY_IOS_SIGNAL_DEBUG_ENABLED)
                    .remove(SettingsStore.KEY_IOS_SIGNAL_DEBUG_SIM1_ENABLED)
                    .remove(SettingsStore.KEY_IOS_SIGNAL_DEBUG_SIM2_ENABLED)
                    .remove(SettingsStore.KEY_IOS_SIGNAL_DEBUG_SIM1_LEVEL)
                    .remove(SettingsStore.KEY_IOS_SIGNAL_DEBUG_SIM2_LEVEL)
                    .apply();
            SettingsStore.notifyChanged(this);
            recreate();
        });
        root.addView(reset, matchWrapWithTop(14));

        setContentView(scrollView);
        refreshRuntimeDebug();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshRuntimeDebug();
    }

    private void refreshRuntimeDebug() {
        if (runtimeDebugView == null) {
            return;
        }
        String summary = "";
        try (Cursor cursor = getContentResolver().query(SettingsStore.SETTINGS_URI,
                null, null, null, null)) {
            if (cursor != null) {
                int keyColumn = cursor.getColumnIndex("key");
                int valueColumn = cursor.getColumnIndex("value");
                while (cursor.moveToNext()) {
                    String key = cursor.getString(keyColumn);
                    if (SettingsStore.KEY_RUNTIME_SIGNAL_DEBUG_SUMMARY.equals(key)) {
                        summary = cursor.getString(valueColumn);
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            summary = "读取运行时状态失败: " + t.getMessage();
        }
        if (summary == null || summary.length() == 0) {
            summary = "还没有收到 SystemUI 进程上报的信号 hook 状态。\n"
                    + "请确认模块已启用并重启过 SystemUI，然后等待信号图标刷新或打开/关闭调试开关。";
        }
        runtimeDebugView.setText(summary);
    }

    private void requestRuntimeDebugRefresh() {
        if (runtimeDebugView != null) {
            runtimeDebugView.setText("正在请求 SystemUI 上报当前 hook 状态...");
        }
        SettingsStore.notifyChanged(this);
        new Handler(Looper.getMainLooper()).postDelayed(this::refreshRuntimeDebug, 400);
        new Handler(Looper.getMainLooper()).postDelayed(this::refreshRuntimeDebug, 1200);
    }

    private void addSlider(LinearLayout root, String title, String subtitle, String key,
            int defaultValue, int min, int max, String unit) {
        LinearLayout card = card();
        TextView titleView = itemTitle(title);
        TextView subtitleView = itemSubtitle(subtitle);
        TextView valueView = itemValue("");
        SeekBar seekBar = new SeekBar(this);

        int current = prefs.getInt(key, defaultValue);
        int clamped = Math.max(min, Math.min(max, current));
        valueView.setText(clamped + unit);
        seekBar.setMax(max - min);
        seekBar.setProgress(clamped - min);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = progress + min;
                valueView.setText(value + unit);
                if (fromUser) {
                    putIntSetting(key, value);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                putIntSetting(key, seekBar.getProgress() + min);
            }
        });

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.addView(titleView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        header.addView(valueView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        card.addView(header, matchWrap());
        card.addView(subtitleView, matchWrap());
        card.addView(seekBar, matchWrapWithTop(8));
        root.addView(card, matchWrapWithTop(10));
    }

    private void addSwitch(LinearLayout root, String title, String subtitle, String key, boolean defaultValue) {
        LinearLayout card = card();
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = itemTitle(title);
        TextView subtitleView = itemSubtitle(subtitle);
        text.addView(titleView, matchWrap());
        text.addView(subtitleView, matchWrap());

        Switch sw = new Switch(this);
        sw.setChecked(prefs.getBoolean(key, defaultValue));
        sw.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) ->
                putBooleanSetting(key, isChecked));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row.addView(sw, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        card.addView(row, matchWrap());
        root.addView(card, matchWrapWithTop(10));
    }

    private void putIntSetting(String key, int value) {
        prefs.edit().putInt(key, value).apply();
        SettingsStore.notifyChanged(this);
    }

    private void putBooleanSetting(String key, boolean value) {
        prefs.edit().putBoolean(key, value).apply();
        SettingsStore.notifyChanged(this);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(roundRect(Color.WHITE, 12));
        return card;
    }

    private TextView itemTitle(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.rgb(32, 33, 36));
        view.setTextSize(16);
        return view;
    }

    private TextView itemSubtitle(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.rgb(95, 99, 104));
        view.setTextSize(13);
        view.setPadding(0, dp(4), 0, 0);
        return view;
    }

    private TextView itemValue(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.rgb(25, 103, 210));
        view.setTextSize(15);
        view.setPadding(dp(12), 0, 0, 0);
        return view;
    }

    private TextView button(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(15);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(16), dp(12), dp(16), dp(12));
        view.setBackground(roundRect(Color.rgb(25, 103, 210), 12));
        return view;
    }

    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
