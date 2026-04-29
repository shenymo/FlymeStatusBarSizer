package com.example.flymestatusbarsizer;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

public class BatterySettingsActivity extends Activity {
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
        title.setText("iOS 风格电池");
        title.setTextColor(Color.rgb(32, 33, 36));
        title.setTextSize(24);
        root.addView(title, matchWrap());

        TextView summary = new TextView(this);
        summary.setText("这些选项只影响代码绘制的 iOS 风格电池图标。");
        summary.setTextColor(Color.rgb(95, 99, 104));
        summary.setTextSize(14);
        summary.setPadding(0, dp(6), 0, dp(14));
        root.addView(summary, matchWrap());

        addSlider(root, "电池长度", "包含右侧电池头的整体绘制宽度。", SettingsStore.KEY_IOS_BATTERY_WIDTH,
                SettingsStore.DEFAULT_IOS_BATTERY_WIDTH, 16, 44, "dp");
        addSlider(root, "电池宽度", "电池主体高度。", SettingsStore.KEY_IOS_BATTERY_HEIGHT,
                SettingsStore.DEFAULT_IOS_BATTERY_HEIGHT, 8, 24, "dp");
        addSlider(root, "左右偏移", "正数向右，负数向左。", SettingsStore.KEY_IOS_BATTERY_OFFSET_X,
                SettingsStore.DEFAULT_IOS_BATTERY_OFFSET_X, -20, 20, "dp");
        addSlider(root, "上下偏移", "正数向下，负数向上。", SettingsStore.KEY_IOS_BATTERY_OFFSET_Y,
                SettingsStore.DEFAULT_IOS_BATTERY_OFFSET_Y, -20, 20, "dp");
        addSlider(root, "内部字体大小", "数字高度占电池高度的比例。", SettingsStore.KEY_IOS_BATTERY_TEXT_SIZE,
                SettingsStore.DEFAULT_IOS_BATTERY_TEXT_SIZE, 40, 100, "%");

        TextView reset = button("恢复本页默认");
        reset.setOnClickListener(v -> {
            prefs.edit()
                    .remove(SettingsStore.KEY_IOS_BATTERY_WIDTH)
                    .remove(SettingsStore.KEY_IOS_BATTERY_HEIGHT)
                    .remove(SettingsStore.KEY_IOS_BATTERY_OFFSET_X)
                    .remove(SettingsStore.KEY_IOS_BATTERY_OFFSET_Y)
                    .remove(SettingsStore.KEY_IOS_BATTERY_TEXT_SIZE)
                    .apply();
            recreate();
        });
        root.addView(reset, matchWrapWithTop(14));

        setContentView(scrollView);
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
                    prefs.edit().putInt(key, value).apply();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                prefs.edit().putInt(key, seekBar.getProgress() + min).apply();
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
