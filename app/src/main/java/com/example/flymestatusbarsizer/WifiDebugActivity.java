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

public class WifiDebugActivity extends Activity {
    private SharedPreferences prefs;
    private TextView eventDebugView;
    private TextView snapshotDebugView;

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
        title.setText("Wi-Fi 调试");
        title.setTextColor(Color.rgb(32, 33, 36));
        title.setTextSize(24);
        root.addView(title, matchWrap());

        TextView summary = new TextView(this);
        summary.setText("可以给 SystemUI 的 WifiIconState 注入假的 Wi-Fi 显示状态和信号等级，再观察模块是否从系统状态链路里正确读回。");
        summary.setTextColor(Color.rgb(95, 99, 104));
        summary.setTextSize(14);
        summary.setPadding(0, dp(6), 0, dp(14));
        root.addView(summary, matchWrap());

        addSwitch(root, "启用假 Wi-Fi 状态", "开启后会直接改写 SystemUI 里的 WifiIconState，再由系统视图刷新图标，模块继续从系统里读取。",
                SettingsStore.KEY_IOS_WIFI_DEBUG_ENABLED,
                SettingsStore.DEFAULT_IOS_WIFI_DEBUG_ENABLED);
        addSwitch(root, "显示 Wi-Fi 图标", "关闭后会把 WifiIconState.visible 设为 false，用来验证系统显隐链路是否正常。",
                SettingsStore.KEY_IOS_WIFI_DEBUG_VISIBLE,
                SettingsStore.DEFAULT_IOS_WIFI_DEBUG_VISIBLE);
        addSlider(root, "Wi-Fi 假信号", "0 到 4 格，直接伪造到 WifiIconState.resId，再看模块是否按系统资源更新。",
                SettingsStore.KEY_IOS_WIFI_DEBUG_LEVEL,
                SettingsStore.DEFAULT_IOS_WIFI_DEBUG_LEVEL, 0, 4, "格");

        eventDebugView = itemSubtitle("");
        eventDebugView.setTextColor(Color.rgb(32, 33, 36));
        LinearLayout eventCard = card();
        eventCard.addView(itemTitle("最近一次原始事件"), matchWrap());
        eventCard.addView(eventDebugView, matchWrapWithTop(8));
        root.addView(eventCard, matchWrapWithTop(10));

        snapshotDebugView = itemSubtitle("");
        snapshotDebugView.setTextColor(Color.rgb(32, 33, 36));
        LinearLayout snapshotCard = card();
        snapshotCard.addView(itemTitle("当前跟踪快照"), matchWrap());
        snapshotCard.addView(snapshotDebugView, matchWrapWithTop(8));
        root.addView(snapshotCard, matchWrapWithTop(10));

        TextView refresh = button("刷新 Hook 状态");
        refresh.setOnClickListener(v -> requestRuntimeDebugRefresh());
        root.addView(refresh, matchWrapWithTop(10));

        TextView reset = button("关闭并恢复真实 Wi-Fi");
        reset.setOnClickListener(v -> {
            prefs.edit()
                    .remove(SettingsStore.KEY_IOS_WIFI_DEBUG_ENABLED)
                    .remove(SettingsStore.KEY_IOS_WIFI_DEBUG_VISIBLE)
                    .remove(SettingsStore.KEY_IOS_WIFI_DEBUG_LEVEL)
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
        if (eventDebugView == null || snapshotDebugView == null) {
            return;
        }
        String eventSummary = "";
        String snapshotSummary = "";
        try (Cursor cursor = getContentResolver().query(SettingsStore.SETTINGS_URI,
                null, null, null, null)) {
            if (cursor != null) {
                int keyColumn = cursor.getColumnIndex("key");
                int valueColumn = cursor.getColumnIndex("value");
                while (cursor.moveToNext()) {
                    String key = cursor.getString(keyColumn);
                    if (SettingsStore.KEY_RUNTIME_WIFI_DEBUG_SUMMARY.equals(key)) {
                        eventSummary = cursor.getString(valueColumn);
                    } else if (SettingsStore.KEY_RUNTIME_WIFI_DEBUG_SNAPSHOT.equals(key)) {
                        snapshotSummary = cursor.getString(valueColumn);
                    }
                }
            }
        } catch (Throwable t) {
            eventSummary = "读取原始事件失败: " + t.getMessage();
            snapshotSummary = "读取跟踪快照失败: " + t.getMessage();
        }
        if (eventSummary == null || eventSummary.length() == 0) {
            eventSummary = "还没有收到 Wi-Fi 原始状态事件。\n请先等待系统自己刷新 Wi-Fi 图标，或者开启调试后切一次 Wi-Fi 开关。";
        }
        if (snapshotSummary == null || snapshotSummary.length() == 0) {
            snapshotSummary = "还没有拿到当前跟踪快照。\n点击“刷新 Hook 状态”后，SystemUI 会回填当前正在跟踪的 Wi-Fi 视图信息。";
        }
        eventDebugView.setText(eventSummary);
        snapshotDebugView.setText(snapshotSummary);
    }

    private void requestRuntimeDebugRefresh() {
        if (snapshotDebugView != null) {
            snapshotDebugView.setText("正在请求 SystemUI 回填当前 Wi-Fi 跟踪快照...");
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
