package com.example.flymestatusbarsizer;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

public class WifiSettingsActivity extends Activity {
    private SharedPreferences prefs;
    private FrameLayout previewBox;
    private FrameLayout iconContainer;
    private ImageView preview;
    private IosWifiDrawable previewDrawable;
    private TextView metricsView;
    private int previewLevel = IosWifiDrawable.MAX_LEVEL;
    private int previewScale = 4;
    private int wifiWidthDp;
    private int wifiHeightDp;
    private int wifiOffsetXDp;
    private int wifiOffsetYDp;
    private int wifiMarginEndDp;
    private boolean showContainer = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = SettingsStore.prefs(this);
        wifiWidthDp = prefs.getInt(SettingsStore.KEY_IOS_WIFI_WIDTH, SettingsStore.DEFAULT_IOS_WIFI_WIDTH);
        wifiHeightDp = prefs.getInt(SettingsStore.KEY_IOS_WIFI_HEIGHT, SettingsStore.DEFAULT_IOS_WIFI_HEIGHT);
        wifiOffsetXDp = prefs.getInt(SettingsStore.KEY_IOS_WIFI_OFFSET_X, SettingsStore.DEFAULT_IOS_WIFI_OFFSET_X);
        wifiOffsetYDp = prefs.getInt(SettingsStore.KEY_IOS_WIFI_OFFSET_Y, SettingsStore.DEFAULT_IOS_WIFI_OFFSET_Y);
        wifiMarginEndDp = prefs.getInt(SettingsStore.KEY_IOS_WIFI_MARGIN_END, SettingsStore.DEFAULT_IOS_WIFI_MARGIN_END);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.rgb(245, 245, 245));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(28));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("Wi-Fi \u56fe\u6807");
        title.setTextColor(Color.rgb(32, 33, 36));
        title.setTextSize(24);
        root.addView(title, matchWrap());

        TextView summary = new TextView(this);
        summary.setText("\u8fd9\u91cc\u8c03\u6574 Wi-Fi \u4fe1\u53f7\u56fe\u6807\u8ddf\u968f\u6574\u4f53\u7f29\u653e\u7684\u5f3a\u5ea6\u3002");
        summary.setTextColor(Color.rgb(95, 99, 104));
        summary.setTextSize(14);
        summary.setPadding(0, dp(6), 0, dp(14));
        root.addView(summary, matchWrap());

        addSwitch(root, "iOS \u98ce\u683c Wi-Fi", "\u6839\u636e SystemUI \u4e0b\u53d1\u7684 wifi_signal \u8d44\u6e90\u52a8\u6001\u7ed8\u5236\u4fe1\u53f7\u5f3a\u5ea6\u3002",
                SettingsStore.KEY_IOS_WIFI_STYLE, SettingsStore.DEFAULT_IOS_WIFI_STYLE);

        addPreview(root);
        addLocalSwitch(root, "\u663e\u793a\u7ea2\u8272\u5bb9\u5668\u8fb9\u6846", showContainer, value -> {
            showContainer = value;
            updatePreview();
        });
        addPreviewSlider(root, "\u9884\u89c8\u7ea7\u522b", -1, IosWifiDrawable.MAX_LEVEL, previewLevel,
                value -> {
                    previewLevel = value;
                    previewDrawable.setLevelValue(previewLevel);
                    updatePreview();
                }, value -> value == IosWifiDrawable.LEVEL_ERROR ? "error" : Integer.toString(value));
        addPreviewSlider(root, "\u9884\u89c8\u500d\u7387", 1, 8, previewScale,
                value -> {
                    previewScale = value;
                    updatePreview();
                }, value -> value + "x");

        addSlider(root, "Wi-Fi \u7f29\u653e\u5f3a\u5ea6", "wifi_signal \u8ddf\u968f\u6574\u4f53\u7f29\u653e\u7684\u5f3a\u5ea6\u3002",
                SettingsStore.KEY_WIFI_SIGNAL_FACTOR, SettingsStore.DEFAULT_WIFI_SIGNAL_FACTOR, 0, 160, "%");
        addSlider(root, "Wi-Fi \u56fe\u6807\u5bbd\u5ea6", "\u81ea\u7ed8 Wi-Fi \u56fe\u6807\u7684\u5e03\u5c40\u5bbd\u5ea6\u3002",
                SettingsStore.KEY_IOS_WIFI_WIDTH, SettingsStore.DEFAULT_IOS_WIFI_WIDTH, 10, 60, "dp");
        addSlider(root, "Wi-Fi \u56fe\u6807\u9ad8\u5ea6", "\u81ea\u7ed8 Wi-Fi \u56fe\u6807\u7684\u5e03\u5c40\u9ad8\u5ea6\u3002",
                SettingsStore.KEY_IOS_WIFI_HEIGHT, SettingsStore.DEFAULT_IOS_WIFI_HEIGHT, 8, 60, "dp");
        addSlider(root, "Wi-Fi \u6c34\u5e73\u504f\u79fb", "\u6b63\u6570\u5411\u53f3\uff0c\u8d1f\u6570\u5411\u5de6\u3002",
                SettingsStore.KEY_IOS_WIFI_OFFSET_X, SettingsStore.DEFAULT_IOS_WIFI_OFFSET_X, -80, 80, "dp");
        addSlider(root, "Wi-Fi \u5782\u76f4\u504f\u79fb", "\u6b63\u6570\u5411\u4e0b\uff0c\u8d1f\u6570\u5411\u4e0a\u3002",
                SettingsStore.KEY_IOS_WIFI_OFFSET_Y, SettingsStore.DEFAULT_IOS_WIFI_OFFSET_Y, -80, 80, "dp");
        addSlider(root, "Wi-Fi \u53f3\u4fa7\u95f4\u8ddd", "\u8c03\u6574 Wi-Fi \u4e0e\u53f3\u4fa7\u56fe\u6807\u7684\u5e03\u5c40\u95f4\u8ddd\u3002",
                SettingsStore.KEY_IOS_WIFI_MARGIN_END, SettingsStore.DEFAULT_IOS_WIFI_MARGIN_END, -80, 80, "dp");

        TextView reset = button("\u6062\u590d\u672c\u9875\u9ed8\u8ba4");
        reset.setOnClickListener(v -> {
            prefs.edit()
                    .remove(SettingsStore.KEY_IOS_WIFI_STYLE)
                    .remove(SettingsStore.KEY_WIFI_SIGNAL_FACTOR)
                    .remove(SettingsStore.KEY_IOS_WIFI_WIDTH)
                    .remove(SettingsStore.KEY_IOS_WIFI_HEIGHT)
                    .remove(SettingsStore.KEY_IOS_WIFI_OFFSET_X)
                    .remove(SettingsStore.KEY_IOS_WIFI_OFFSET_Y)
                    .remove(SettingsStore.KEY_IOS_WIFI_MARGIN_END)
                    .apply();
            recreate();
        });
        root.addView(reset, matchWrapWithTop(14));

        setContentView(scrollView);
    }

    private void addPreview(LinearLayout root) {
        previewBox = new FrameLayout(this);
        previewBox.setBackground(roundRect(Color.rgb(24, 24, 24), 12));
        previewBox.setClipChildren(false);
        previewBox.setClipToPadding(false);
        previewBox.setPadding(dp(16), dp(16), dp(16), dp(16));

        previewDrawable = new IosWifiDrawable(previewLevel, getResources().getDisplayMetrics().density);
        previewDrawable.setTint(Color.WHITE);
        preview = new ImageView(this);
        preview.setImageDrawable(previewDrawable);
        preview.setAdjustViewBounds(false);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iconContainer = new FrameLayout(this);
        iconContainer.setClipChildren(false);
        iconContainer.setClipToPadding(false);
        iconContainer.addView(preview, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        previewBox.addView(iconContainer);
        root.addView(previewBox, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(180)));

        metricsView = itemSubtitle("");
        metricsView.setPadding(0, dp(8), 0, dp(4));
        root.addView(metricsView, matchWrap());
        updatePreview();
    }

    private void updatePreview() {
        int width = dp(wifiWidthDp * previewScale);
        int height = dp(wifiHeightDp * previewScale);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, height);
        lp.gravity = Gravity.CENTER;
        iconContainer.setLayoutParams(lp);
        iconContainer.setTranslationX(dp(wifiOffsetXDp * previewScale));
        iconContainer.setTranslationY(dp(wifiOffsetYDp * previewScale));
        iconContainer.setBackground(showContainer ? strokeRect(Color.TRANSPARENT, Color.RED, 1, 0) : null);
        metricsView.setText("container: " + wifiWidthDp + "dp x " + wifiHeightDp + "dp  /  preview: "
                + width + "px x " + height + "px  /  offset: "
                + wifiOffsetXDp + "dp, " + wifiOffsetYDp + "dp  /  marginEnd: "
                + wifiMarginEndDp + "dp");
        preview.invalidate();
        iconContainer.invalidate();
        previewBox.invalidate();
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
                    updateWifiPreviewValue(key, value);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int value = seekBar.getProgress() + min;
                prefs.edit().putInt(key, value).apply();
                updateWifiPreviewValue(key, value);
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

    private void updateWifiPreviewValue(String key, int value) {
        if (SettingsStore.KEY_IOS_WIFI_WIDTH.equals(key)) {
            wifiWidthDp = value;
        } else if (SettingsStore.KEY_IOS_WIFI_HEIGHT.equals(key)) {
            wifiHeightDp = value;
        } else if (SettingsStore.KEY_IOS_WIFI_OFFSET_X.equals(key)) {
            wifiOffsetXDp = value;
        } else if (SettingsStore.KEY_IOS_WIFI_OFFSET_Y.equals(key)) {
            wifiOffsetYDp = value;
        } else if (SettingsStore.KEY_IOS_WIFI_MARGIN_END.equals(key)) {
            wifiMarginEndDp = value;
        } else {
            return;
        }
        updatePreview();
    }

    private void addPreviewSlider(LinearLayout root, String title, int min, int max, int current,
            ValueConsumer consumer, ValueLabel label) {
        int clamped = Math.max(min, Math.min(max, current));
        LinearLayout card = card();
        TextView titleView = itemTitle(title);
        TextView valueView = itemValue(label.text(clamped));
        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(max - min);
        seekBar.setProgress(clamped - min);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = progress + min;
                valueView.setText(label.text(value));
                consumer.accept(value);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.addView(titleView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        header.addView(valueView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        card.addView(header, matchWrap());
        card.addView(seekBar, matchWrapWithTop(8));
        root.addView(card, matchWrapWithTop(10));
    }

    private void addLocalSwitch(LinearLayout root, String title, boolean defaultValue, BooleanConsumer consumer) {
        LinearLayout card = card();
        TextView titleView = itemTitle(title);
        Switch sw = new Switch(this);
        sw.setChecked(defaultValue);
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> consumer.accept(isChecked));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(titleView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row.addView(sw, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        card.addView(row, matchWrap());
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
                prefs.edit().putBoolean(key, isChecked).apply());

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row.addView(sw, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        card.addView(row, matchWrap());
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

    private GradientDrawable strokeRect(int color, int strokeColor, int strokeWidthDp, int radiusDp) {
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private interface ValueConsumer {
        void accept(int value);
    }

    private interface ValueLabel {
        String text(int value);
    }

    private interface BooleanConsumer {
        void accept(boolean value);
    }
}
