package com.example.flymestatusbarsizer;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

public class MainActivity extends Activity {
    private SharedPreferences prefs;

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
        title.setText("Flyme 状态栏调整");
        title.setTextColor(Color.rgb(32, 33, 36));
        title.setTextSize(24);
        title.setGravity(Gravity.START);
        root.addView(title, matchWrap());

        TextView summary = new TextView(this);
        summary.setText("调整后重启 SystemUI 或重启手机生效。数值过大可能导致裁切。");
        summary.setTextColor(Color.rgb(95, 99, 104));
        summary.setTextSize(14);
        summary.setPadding(0, dp(6), 0, dp(14));
        root.addView(summary, matchWrap());

        addSwitch(root, "启用模块调整", "关闭后 hook 仍加载，但不修改尺寸",
                SettingsStore.KEY_ENABLED, SettingsStore.DEFAULT_ENABLED);
        addSwitch(root, "隐藏 4G / 5G 标识", "只隐藏网络类型文字/图标，不隐藏信号格",
                SettingsStore.KEY_HIDE_MOBILE_TYPE, SettingsStore.DEFAULT_HIDE_MOBILE_TYPE);
        addSwitch(root, "iOS 风格电池", "用代码绘制灰底、白色电量和黑色数字",
                SettingsStore.KEY_IOS_BATTERY_STYLE, SettingsStore.DEFAULT_IOS_BATTERY_STYLE);
        addSwitch(root, "iOS \u98ce\u683c\u79fb\u52a8\u4fe1\u53f7\u683c", "\u8bd5\u9a8c\u529f\u80fd\uff1a\u7528\u4ee3\u7801\u7ed8\u5236\u7684\u56fa\u5b9a\u6ee1\u683c\u4fe1\u53f7\u66ff\u6362 mobile_signal",
                SettingsStore.KEY_IOS_SIGNAL_STYLE, SettingsStore.DEFAULT_IOS_SIGNAL_STYLE);
        addSwitch(root, "iOS \u98ce\u683c 5G \u6807\u8bc6", "\u53ea\u663e\u793a 5G / 5GA / 5G+\uff0c\u5176\u4ed6\u7f51\u7edc\u7c7b\u578b\u6807\u8bc6\u81ea\u52a8\u9690\u85cf",
                SettingsStore.KEY_IOS_NETWORK_TYPE_STYLE, SettingsStore.DEFAULT_IOS_NETWORK_TYPE_STYLE);

        addSlider(root, "状态栏整体图标缩放", "移动信号、Wi-Fi、电池、上下行箭头和普通图标一起按原始尺寸缩放",
                SettingsStore.KEY_GLOBAL_ICON_SCALE, SettingsStore.DEFAULT_GLOBAL_ICON_SCALE, 80, 160, "%");

        TextView advanced = button("详细图标设置");
        advanced.setOnClickListener(v -> startActivity(new Intent(this, AdvancedSettingsActivity.class)));
        root.addView(advanced, matchWrapWithTop(10));

        TextView battery = button("iOS 风格电池设置");
        battery.setOnClickListener(v -> startActivity(new Intent(this, BatterySettingsActivity.class)));
        root.addView(battery, matchWrapWithTop(10));

        addSlider(root, "文字大小", "状态栏时钟等文字单独缩放",
                SettingsStore.KEY_TEXT_SCALE, SettingsStore.DEFAULT_TEXT_SCALE, 80, 130, "%");

        TextView reset = button("恢复默认");
        reset.setOnClickListener(v -> {
            prefs.edit().clear().apply();
            recreate();
        });
        root.addView(reset, matchWrapWithTop(14));

        setContentView(scrollView);
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
                prefs.edit().putBoolean(key, isChecked).apply());

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row.addView(sw, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        card.addView(row, matchWrap());
        root.addView(card, matchWrapWithTop(10));
    }

    private void addSlider(LinearLayout root, String title, String subtitle, String key,
                           int defaultValue, int min, int max, String suffix) {
        LinearLayout card = card();
        TextView titleView = itemTitle(title);
        TextView subtitleView = itemSubtitle(subtitle);
        TextView valueView = itemValue("");
        SeekBar seekBar = new SeekBar(this);

        int current = prefs.getInt(key, defaultValue);
        valueView.setText(current + suffix);
        seekBar.setMax(max - min);
        seekBar.setProgress(Math.max(0, Math.min(max - min, current - min)));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = min + progress;
                valueView.setText(value + suffix);
                if (fromUser) {
                    prefs.edit().putInt(key, value).apply();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                prefs.edit().putInt(key, min + seekBar.getProgress()).apply();
            }
        });

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.addView(titleView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        header.addView(valueView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        card.addView(header, matchWrap());
        card.addView(subtitleView, matchWrap());
        card.addView(seekBar, matchWrapWithTop(8));
        root.addView(card, matchWrapWithTop(10));
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
