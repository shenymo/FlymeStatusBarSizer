package com.example.flymestatusbarsizer;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int REQUEST_EXPORT_CONFIG = 1001;
    private static final int REQUEST_IMPORT_CONFIG = 1002;

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
        title.setText("Flyme \u72b6\u6001\u680f\u8c03\u6574");
        title.setTextColor(Color.rgb(32, 33, 36));
        title.setTextSize(24);
        title.setGravity(Gravity.START);
        root.addView(title, matchWrap());

        TextView summary = new TextView(this);
        summary.setText("\u8c03\u6574\u540e\u91cd\u542f SystemUI \u6216\u91cd\u542f\u624b\u673a\u751f\u6548\u3002\u6570\u503c\u8fc7\u5927\u53ef\u80fd\u5bfc\u81f4\u88c1\u5207\u3002");
        summary.setTextColor(Color.rgb(95, 99, 104));
        summary.setTextSize(14);
        summary.setPadding(0, dp(6), 0, dp(14));
        root.addView(summary, matchWrap());

        addSwitch(root, "\u542f\u7528\u6a21\u5757\u8c03\u6574", "\u5173\u95ed\u540e hook \u4ecd\u52a0\u8f7d\uff0c\u4f46\u4e0d\u4fee\u6539\u5c3a\u5bf8",
                SettingsStore.KEY_ENABLED, SettingsStore.DEFAULT_ENABLED);
        addSwitch(root, "iOS \u98ce\u683c\u7535\u6c60", "\u7528\u4ee3\u7801\u7ed8\u5236\u7070\u5e95\u3001\u767d\u8272\u7535\u91cf\u548c\u9ed1\u8272\u6570\u5b57",
                SettingsStore.KEY_IOS_BATTERY_STYLE, SettingsStore.DEFAULT_IOS_BATTERY_STYLE);
        addSwitch(root, "iOS \u98ce\u683c\u79fb\u52a8\u4fe1\u53f7\u683c", "\u8bd5\u9a8c\u529f\u80fd\uff1a\u7528\u4ee3\u7801\u7ed8\u5236\u7684\u56fa\u5b9a\u6ee1\u683c\u4fe1\u53f7\u66ff\u6362 mobile_signal",
                SettingsStore.KEY_IOS_SIGNAL_STYLE, SettingsStore.DEFAULT_IOS_SIGNAL_STYLE);
        addSwitch(root, "iOS \u98ce\u683c 5G \u6807\u8bc6", "\u53ea\u663e\u793a 5G / 5GA / 5G+\uff0c\u5176\u4ed6\u7f51\u7edc\u7c7b\u578b\u6807\u8bc6\u81ea\u52a8\u9690\u85cf",
                SettingsStore.KEY_IOS_NETWORK_TYPE_STYLE, SettingsStore.DEFAULT_IOS_NETWORK_TYPE_STYLE);
        addSwitch(root, "iOS \u98ce\u683c Wi-Fi", "\u6839\u636e wifi_signal \u8d44\u6e90\u52a8\u6001\u7ed8\u5236 Wi-Fi \u5f3a\u5ea6",
                SettingsStore.KEY_IOS_WIFI_STYLE, SettingsStore.DEFAULT_IOS_WIFI_STYLE);

        addSlider(root, "\u72b6\u6001\u680f\u6574\u4f53\u56fe\u6807\u7f29\u653e", "\u79fb\u52a8\u4fe1\u53f7\u3001Wi-Fi\u3001\u7535\u6c60\u3001\u4e0a\u4e0b\u884c\u7bad\u5934\u548c\u666e\u901a\u56fe\u6807\u4e00\u8d77\u6309\u539f\u59cb\u5c3a\u5bf8\u7f29\u653e",
                SettingsStore.KEY_GLOBAL_ICON_SCALE, SettingsStore.DEFAULT_GLOBAL_ICON_SCALE, 80, 160, "%");

        TextView signalNetwork = button("iOS \u4fe1\u53f7\u683c\u4e0e 5G \u6807\u8bc6\u8bbe\u7f6e");
        signalNetwork.setOnClickListener(v -> startActivity(new Intent(this, SignalNetworkSettingsActivity.class)));
        root.addView(signalNetwork, matchWrapWithTop(10));

        TextView battery = button("iOS \u98ce\u683c\u7535\u6c60\u8bbe\u7f6e");
        battery.setOnClickListener(v -> startActivity(new Intent(this, BatterySettingsActivity.class)));
        root.addView(battery, matchWrapWithTop(10));

        TextView wifi = button("Wi-Fi \u56fe\u6807\u8bbe\u7f6e");
        wifi.setOnClickListener(v -> startActivity(new Intent(this, WifiSettingsActivity.class)));
        root.addView(wifi, matchWrapWithTop(10));

        TextView otherIcons = button("\u5176\u4ed6\u56fe\u6807\u8bbe\u7f6e");
        otherIcons.setOnClickListener(v -> startActivity(new Intent(this, OtherIconSettingsActivity.class)));
        root.addView(otherIcons, matchWrapWithTop(10));

        TextView exportConfig = button("\u5bfc\u51fa\u5f53\u524d\u914d\u7f6e");
        exportConfig.setOnClickListener(v -> startExportConfig());
        root.addView(exportConfig, matchWrapWithTop(10));

        TextView importConfig = button("\u5bfc\u5165\u914d\u7f6e");
        importConfig.setOnClickListener(v -> startImportConfig());
        root.addView(importConfig, matchWrapWithTop(10));

        addSlider(root, "\u6587\u5b57\u5927\u5c0f", "\u72b6\u6001\u680f\u65f6\u949f\u7b49\u6587\u5b57\u5355\u72ec\u7f29\u653e",
                SettingsStore.KEY_TEXT_SCALE, SettingsStore.DEFAULT_TEXT_SCALE, 80, 130, "%");

        TextView reset = button("\u6062\u590d\u9ed8\u8ba4");
        reset.setOnClickListener(v -> {
            prefs.edit().clear().apply();
            recreate();
        });
        root.addView(reset, matchWrapWithTop(14));

        setContentView(scrollView);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        if (requestCode == REQUEST_EXPORT_CONFIG) {
            exportConfig(uri);
        } else if (requestCode == REQUEST_IMPORT_CONFIG) {
            importConfig(uri);
        }
    }

    private void startExportConfig() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "flyme_status_bar_sizer_config.json");
        startActivityForResult(intent, REQUEST_EXPORT_CONFIG);
    }

    private void startImportConfig() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent, REQUEST_IMPORT_CONFIG);
    }

    private void exportConfig(Uri uri) {
        try (OutputStream output = getContentResolver().openOutputStream(uri)) {
            if (output == null) {
                showToast("\u65e0\u6cd5\u6253\u5f00\u5bfc\u51fa\u6587\u4ef6");
                return;
            }
            JSONObject root = new JSONObject();
            JSONObject settings = new JSONObject();
            root.put("schema", "flyme_status_bar_sizer");
            root.put("version", 1);
            for (String key : SettingsStore.BOOLEAN_KEYS) {
                settings.put(key, prefs.getBoolean(key, SettingsStore.defaultBoolean(key)));
            }
            for (String key : SettingsStore.INT_KEYS) {
                settings.put(key, prefs.getInt(key, SettingsStore.defaultInt(key)));
            }
            root.put("settings", settings);
            output.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
            showToast("\u914d\u7f6e\u5df2\u5bfc\u51fa");
        } catch (Throwable t) {
            showToast("\u5bfc\u51fa\u5931\u8d25\uff1a" + t.getMessage());
        }
    }

    private void importConfig(Uri uri) {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) {
                showToast("\u65e0\u6cd5\u6253\u5f00\u5bfc\u5165\u6587\u4ef6");
                return;
            }
            JSONObject root = new JSONObject(readText(input));
            JSONObject settings = root.optJSONObject("settings");
            if (settings == null) {
                settings = root;
            }
            SharedPreferences.Editor editor = prefs.edit().clear();
            for (String key : SettingsStore.BOOLEAN_KEYS) {
                editor.putBoolean(key, settings.optBoolean(key, SettingsStore.defaultBoolean(key)));
            }
            for (String key : SettingsStore.INT_KEYS) {
                editor.putInt(key, settings.optInt(key, SettingsStore.defaultInt(key)));
            }
            editor.apply();
            showToast("\u914d\u7f6e\u5df2\u5bfc\u5165");
            recreate();
        } catch (Throwable t) {
            showToast("\u5bfc\u5165\u5931\u8d25\uff1a" + t.getMessage());
        }
    }

    private String readText(InputStream input) throws java.io.IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
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
