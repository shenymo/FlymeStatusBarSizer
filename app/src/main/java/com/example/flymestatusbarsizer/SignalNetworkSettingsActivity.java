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

public class SignalNetworkSettingsActivity extends Activity {
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
        title.setText("iOS \u4fe1\u53f7\u683c\u4e0e 5G \u6807\u8bc6");
        title.setTextColor(Color.rgb(32, 33, 36));
        title.setTextSize(24);
        root.addView(title, matchWrap());

        TextView summary = new TextView(this);
        summary.setText("\u8fd9\u91cc\u5355\u72ec\u8c03\u6574 iOS \u98ce\u683c\u79fb\u52a8\u4fe1\u53f7\u683c\u548c 5G / 5GA / 5G+ \u6807\u8bc6\u7684\u5927\u5c0f\u4e0e\u4f4d\u7f6e\u3002");
        summary.setTextColor(Color.rgb(95, 99, 104));
        summary.setTextSize(14);
        summary.setPadding(0, dp(6), 0, dp(14));
        root.addView(summary, matchWrap());

        addSectionTitle(root, "iOS \u79fb\u52a8\u4fe1\u53f7\u683c");
        addSlider(root, "\u79fb\u52a8\u4fe1\u53f7\u7f29\u653e\u5f3a\u5ea6", "mobile_signal \u8ddf\u968f\u6574\u4f53\u7f29\u653e\u7684\u5f3a\u5ea6\u3002",
                SettingsStore.KEY_MOBILE_SIGNAL_FACTOR, SettingsStore.DEFAULT_MOBILE_SIGNAL_FACTOR, 0, 160, "%");
        addOffsetSliderWithFallback(root, "\u684c\u9762\u72b6\u6001\u680f\u5de6\u53f3\u504f\u79fb",
                "\u4ec5\u5728 iOS \u98ce\u683c\u79fb\u52a8\u4fe1\u53f7\u683c\u5f00\u542f\u65f6\u751f\u6548\uff0c\u6b63\u6570\u5411\u53f3\uff0c\u8d1f\u6570\u5411\u5de6\u3002",
                SettingsStore.KEY_IOS_SIGNAL_DESKTOP_OFFSET_X, SettingsStore.DEFAULT_IOS_SIGNAL_DESKTOP_OFFSET_X,
                SettingsStore.KEY_IOS_SIGNAL_OFFSET_X, SettingsStore.DEFAULT_IOS_SIGNAL_OFFSET_X);
        addOffsetSliderWithFallback(root, "\u684c\u9762\u72b6\u6001\u680f\u4e0a\u4e0b\u504f\u79fb",
                "\u4ec5\u5728 iOS \u98ce\u683c\u79fb\u52a8\u4fe1\u53f7\u683c\u5f00\u542f\u65f6\u751f\u6548\uff0c\u6b63\u6570\u5411\u4e0b\uff0c\u8d1f\u6570\u5411\u4e0a\u3002",
                SettingsStore.KEY_IOS_SIGNAL_DESKTOP_OFFSET_Y, SettingsStore.DEFAULT_IOS_SIGNAL_DESKTOP_OFFSET_Y,
                SettingsStore.KEY_IOS_SIGNAL_OFFSET_Y, SettingsStore.DEFAULT_IOS_SIGNAL_OFFSET_Y);
        addOffsetSliderWithFallback(root, "\u9501\u5c4f\u72b6\u6001\u680f\u5de6\u53f3\u504f\u79fb",
                "\u9501\u5c4f\u53f3\u4e0a\u89d2\u7684 iOS \u79fb\u52a8\u4fe1\u53f7\u683c\u5355\u72ec\u8c03\u6574\u3002",
                SettingsStore.KEY_IOS_SIGNAL_KEYGUARD_OFFSET_X, SettingsStore.DEFAULT_IOS_SIGNAL_KEYGUARD_OFFSET_X,
                SettingsStore.KEY_IOS_SIGNAL_OFFSET_X, SettingsStore.DEFAULT_IOS_SIGNAL_OFFSET_X);
        addOffsetSliderWithFallback(root, "\u9501\u5c4f\u72b6\u6001\u680f\u4e0a\u4e0b\u504f\u79fb",
                "\u9501\u5c4f\u53f3\u4e0a\u89d2\u7684 iOS \u79fb\u52a8\u4fe1\u53f7\u683c\u5355\u72ec\u8c03\u6574\u3002",
                SettingsStore.KEY_IOS_SIGNAL_KEYGUARD_OFFSET_Y, SettingsStore.DEFAULT_IOS_SIGNAL_KEYGUARD_OFFSET_Y,
                SettingsStore.KEY_IOS_SIGNAL_OFFSET_Y, SettingsStore.DEFAULT_IOS_SIGNAL_OFFSET_Y);
        addOffsetSliderWithFallback(root, "\u63a7\u5236\u4e2d\u5fc3\u72b6\u6001\u680f\u5de6\u53f3\u504f\u79fb",
                "\u63a7\u5236\u4e2d\u5fc3\u9876\u90e8\u548c\u8fd0\u8425\u5546\u533a\u4f1a\u5171\u7528\u8fd9\u7ec4 iOS \u79fb\u52a8\u4fe1\u53f7\u504f\u79fb\u3002",
                SettingsStore.KEY_IOS_SIGNAL_CONTROL_CENTER_OFFSET_X, SettingsStore.DEFAULT_IOS_SIGNAL_CONTROL_CENTER_OFFSET_X,
                SettingsStore.KEY_IOS_SIGNAL_OFFSET_X, SettingsStore.DEFAULT_IOS_SIGNAL_OFFSET_X);
        addOffsetSliderWithFallback(root, "\u63a7\u5236\u4e2d\u5fc3\u72b6\u6001\u680f\u4e0a\u4e0b\u504f\u79fb",
                "\u63a7\u5236\u4e2d\u5fc3\u9876\u90e8\u548c\u8fd0\u8425\u5546\u533a\u4f1a\u5171\u7528\u8fd9\u7ec4 iOS \u79fb\u52a8\u4fe1\u53f7\u504f\u79fb\u3002",
                SettingsStore.KEY_IOS_SIGNAL_CONTROL_CENTER_OFFSET_Y, SettingsStore.DEFAULT_IOS_SIGNAL_CONTROL_CENTER_OFFSET_Y,
                SettingsStore.KEY_IOS_SIGNAL_OFFSET_Y, SettingsStore.DEFAULT_IOS_SIGNAL_OFFSET_Y);

        addSectionTitle(root, "5G \u6807\u8bc6");
        addSlider(root, "5G \u6807\u8bc6\u7f29\u653e\u5f3a\u5ea6", "mobile_type \u548c VoLTE \u6807\u8bc6\u8ddf\u968f\u6574\u4f53\u7f29\u653e\u7684\u5f3a\u5ea6\u3002",
                SettingsStore.KEY_NETWORK_TYPE_FACTOR, SettingsStore.DEFAULT_NETWORK_TYPE_FACTOR, 0, 160, "%");
        addOffsetSliderWithFallback(root, "\u684c\u9762\u72b6\u6001\u680f\u5de6\u53f3\u504f\u79fb",
                "\u6b63\u6570\u5411\u53f3\uff0c\u8d1f\u6570\u5411\u5de6\uff0c\u7528\u6765\u5728\u653e\u5927\u540e\u5bf9\u9f50 5G / 5GA / 5G+ \u6807\u8bc6\u3002",
                SettingsStore.KEY_NETWORK_TYPE_DESKTOP_OFFSET_X, SettingsStore.DEFAULT_NETWORK_TYPE_DESKTOP_OFFSET_X,
                SettingsStore.KEY_NETWORK_TYPE_OFFSET_X, SettingsStore.DEFAULT_NETWORK_TYPE_OFFSET_X);
        addOffsetSliderWithFallback(root, "\u684c\u9762\u72b6\u6001\u680f\u4e0a\u4e0b\u504f\u79fb",
                "\u6b63\u6570\u5411\u4e0b\uff0c\u8d1f\u6570\u5411\u4e0a\uff0c\u7528\u6765\u8c03\u6574 5G / 5GA / 5G+ \u6807\u8bc6\u9ad8\u5ea6\u3002",
                SettingsStore.KEY_NETWORK_TYPE_DESKTOP_OFFSET_Y, SettingsStore.DEFAULT_NETWORK_TYPE_DESKTOP_OFFSET_Y,
                SettingsStore.KEY_NETWORK_TYPE_OFFSET_Y, SettingsStore.DEFAULT_NETWORK_TYPE_OFFSET_Y);
        addOffsetSliderWithFallback(root, "\u9501\u5c4f\u72b6\u6001\u680f\u5de6\u53f3\u504f\u79fb",
                "\u9501\u5c4f\u53f3\u4e0a\u89d2\u7684 5G / 5GA / 5G+ \u6807\u8bc6\u5355\u72ec\u8c03\u6574\u3002",
                SettingsStore.KEY_NETWORK_TYPE_KEYGUARD_OFFSET_X, SettingsStore.DEFAULT_NETWORK_TYPE_KEYGUARD_OFFSET_X,
                SettingsStore.KEY_NETWORK_TYPE_OFFSET_X, SettingsStore.DEFAULT_NETWORK_TYPE_OFFSET_X);
        addOffsetSliderWithFallback(root, "\u9501\u5c4f\u72b6\u6001\u680f\u4e0a\u4e0b\u504f\u79fb",
                "\u9501\u5c4f\u53f3\u4e0a\u89d2\u7684 5G / 5GA / 5G+ \u6807\u8bc6\u5355\u72ec\u8c03\u6574\u3002",
                SettingsStore.KEY_NETWORK_TYPE_KEYGUARD_OFFSET_Y, SettingsStore.DEFAULT_NETWORK_TYPE_KEYGUARD_OFFSET_Y,
                SettingsStore.KEY_NETWORK_TYPE_OFFSET_Y, SettingsStore.DEFAULT_NETWORK_TYPE_OFFSET_Y);
        addOffsetSliderWithFallback(root, "\u63a7\u5236\u4e2d\u5fc3\u72b6\u6001\u680f\u5de6\u53f3\u504f\u79fb",
                "\u63a7\u5236\u4e2d\u5fc3\u9876\u90e8\u548c\u8fd0\u8425\u5546\u533a\u4f1a\u5171\u7528\u8fd9\u7ec4 5G / 5GA / 5G+ \u504f\u79fb\u3002",
                SettingsStore.KEY_NETWORK_TYPE_CONTROL_CENTER_OFFSET_X, SettingsStore.DEFAULT_NETWORK_TYPE_CONTROL_CENTER_OFFSET_X,
                SettingsStore.KEY_NETWORK_TYPE_OFFSET_X, SettingsStore.DEFAULT_NETWORK_TYPE_OFFSET_X);
        addOffsetSliderWithFallback(root, "\u63a7\u5236\u4e2d\u5fc3\u72b6\u6001\u680f\u4e0a\u4e0b\u504f\u79fb",
                "\u63a7\u5236\u4e2d\u5fc3\u9876\u90e8\u548c\u8fd0\u8425\u5546\u533a\u4f1a\u5171\u7528\u8fd9\u7ec4 5G / 5GA / 5G+ \u504f\u79fb\u3002",
                SettingsStore.KEY_NETWORK_TYPE_CONTROL_CENTER_OFFSET_Y, SettingsStore.DEFAULT_NETWORK_TYPE_CONTROL_CENTER_OFFSET_Y,
                SettingsStore.KEY_NETWORK_TYPE_OFFSET_Y, SettingsStore.DEFAULT_NETWORK_TYPE_OFFSET_Y);
        TextView reset = button("\u6062\u590d\u672c\u9875\u9ed8\u8ba4");
        reset.setOnClickListener(v -> {
            prefs.edit()
                    .remove(SettingsStore.KEY_MOBILE_SIGNAL_FACTOR)
                    .remove(SettingsStore.KEY_NETWORK_TYPE_FACTOR)
                    .remove(SettingsStore.KEY_NETWORK_TYPE_OFFSET_X)
                    .remove(SettingsStore.KEY_NETWORK_TYPE_OFFSET_Y)
                    .remove(SettingsStore.KEY_NETWORK_TYPE_DESKTOP_OFFSET_X)
                    .remove(SettingsStore.KEY_NETWORK_TYPE_DESKTOP_OFFSET_Y)
                    .remove(SettingsStore.KEY_NETWORK_TYPE_KEYGUARD_OFFSET_X)
                    .remove(SettingsStore.KEY_NETWORK_TYPE_KEYGUARD_OFFSET_Y)
                    .remove(SettingsStore.KEY_NETWORK_TYPE_CONTROL_CENTER_OFFSET_X)
                    .remove(SettingsStore.KEY_NETWORK_TYPE_CONTROL_CENTER_OFFSET_Y)
                    .remove(SettingsStore.KEY_IOS_SIGNAL_OFFSET_X)
                    .remove(SettingsStore.KEY_IOS_SIGNAL_OFFSET_Y)
                    .remove(SettingsStore.KEY_IOS_SIGNAL_DESKTOP_OFFSET_X)
                    .remove(SettingsStore.KEY_IOS_SIGNAL_DESKTOP_OFFSET_Y)
                    .remove(SettingsStore.KEY_IOS_SIGNAL_KEYGUARD_OFFSET_X)
                    .remove(SettingsStore.KEY_IOS_SIGNAL_KEYGUARD_OFFSET_Y)
                    .remove(SettingsStore.KEY_IOS_SIGNAL_CONTROL_CENTER_OFFSET_X)
                    .remove(SettingsStore.KEY_IOS_SIGNAL_CONTROL_CENTER_OFFSET_Y)
                    .apply();
            recreate();
        });
        root.addView(reset, matchWrapWithTop(14));

        setContentView(scrollView);
    }

    private void addOffsetSliderWithFallback(LinearLayout root, String title, String subtitle, String key,
            int defaultValue, String fallbackKey, int fallbackDefaultValue) {
        addSlider(root, title, subtitle, key, getOffsetValue(key, defaultValue, fallbackKey, fallbackDefaultValue),
                -20, 20, "dp");
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
        header.addView(valueView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        card.addView(header, matchWrap());
        card.addView(subtitleView, matchWrap());
        card.addView(seekBar, matchWrapWithTop(8));
        root.addView(card, matchWrapWithTop(10));
    }

    private int getOffsetValue(String key, int defaultValue, String fallbackKey, int fallbackDefaultValue) {
        if (prefs.contains(key)) {
            return prefs.getInt(key, defaultValue);
        }
        return prefs.getInt(fallbackKey, fallbackDefaultValue);
    }

    private void addSectionTitle(LinearLayout root, String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.rgb(32, 33, 36));
        view.setTextSize(18);
        view.setPadding(0, dp(16), 0, dp(2));
        root.addView(view, matchWrap());
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
