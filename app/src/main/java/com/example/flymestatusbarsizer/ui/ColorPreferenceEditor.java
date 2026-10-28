package com.example.flymestatusbarsizer.ui;

import com.example.flymestatusbarsizer.MainActivity;
import com.example.flymestatusbarsizer.config.SettingsStore;

import android.app.AlertDialog;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

public final class ColorPreferenceEditor {
    private final MainActivity activity;

    public ColorPreferenceEditor(MainActivity activity) {
        this.activity = activity;
    }

    public void addColor(LinearLayout root, String title, String key, int defaultColor) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, activity.dp(4), 0, activity.dp(4));
        TextView label = new TextView(activity);
        label.setText(title);
        label.setTextSize(16);
        label.setTextColor(activity.textColor());
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));

        TextView swatch = new TextView(activity);
        swatch.setTextSize(14);
        swatch.setGravity(Gravity.CENTER);
        swatch.setPadding(activity.dp(12), activity.dp(8), activity.dp(12), activity.dp(8));
        swatch.setMinHeight(activity.dp(44));
        swatch.setContentDescription(title + "颜色，点击修改");
        updateSwatch(swatch, SettingsStore.readInt(activity.prefs(), key, defaultColor));
        row.addView(swatch);
        activity.setTapClickListener(swatch, v -> showColorDialog(title, key, defaultColor, swatch));
        root.addView(row);
    }

    private void showColorDialog(String title, String key, int defaultColor, TextView swatch) {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(activity.dp(20), activity.dp(8), activity.dp(20), 0);
        TextView preview = new TextView(activity);
        preview.setGravity(Gravity.CENTER);
        content.addView(preview, new LinearLayout.LayoutParams(-1, activity.dp(44)));
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setHint("#RRGGBB，例如 #088BFF");
        input.setContentDescription("十六进制颜色");
        content.addView(input);
        ColorPaletteView palette = new ColorPaletteView(activity, color -> input.setText(hex(color)));
        content.addView(palette, 0);
        SeekBar[] channels = new SeekBar[3];
        String[] labels = {"红", "绿", "蓝"};
        for (int i = 0; i < channels.length; i++) {
            LinearLayout channelRow = new LinearLayout(activity);
            channelRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView channelLabel = new TextView(activity);
            channelLabel.setText(labels[i]);
            channelLabel.setTextSize(15);
            channelLabel.setTextColor(activity.textColor());
            channelRow.addView(channelLabel, new LinearLayout.LayoutParams(
                    activity.dp(42), -2));
            SeekBar slider = new SeekBar(activity);
            slider.setMax(255);
            slider.setContentDescription(labels[i] + "色分量");
            activity.styleSeekBar(slider);
            channels[i] = slider;
            channelRow.addView(slider, new LinearLayout.LayoutParams(-1, activity.dp(40)));
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                    if (fromUser) input.setText(hex(Color.rgb(channels[0].getProgress(),
                            channels[1].getProgress(), channels[2].getProgress())));
                }
                @Override public void onStartTrackingTouch(SeekBar bar) { }
                @Override public void onStopTrackingTouch(SeekBar bar) { }
            });
            content.addView(channelRow);
        }
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                try {
                    int color = parseColor(s.toString());
                    input.setError(null);
                    updateSwatch(preview, color);
                    palette.setCurrentColor(color);
                    channels[0].setProgress(Color.red(color));
                    channels[1].setProgress(Color.green(color));
                    channels[2].setProgress(Color.blue(color));
                } catch (IllegalArgumentException ignored) {
                    palette.setCurrentColor(null);
                }
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        input.setText(hex(SettingsStore.readInt(activity.prefs(), key, defaultColor)));
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(content);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(title + "颜色")
                .setMessage("输入色值或拖动红、绿、蓝滑块。支持六位十六进制颜色。")
                .setView(scroll).setNegativeButton("取消", null)
                .setNeutralButton("恢复默认", (d, w) -> {
                    palette.recordApplied(defaultColor);
                    activity.putIntSetting(key, defaultColor);
                    updateSwatch(swatch, defaultColor);
                })
                .setPositiveButton("应用", null).create();
        dialog.show();
        activity.styleDialog(dialog);
        activity.attachDialogButtonHaptics(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                int color = parseColor(input.getText().toString());
                palette.recordApplied(color);
                activity.putIntSetting(key, color);
                updateSwatch(swatch, color);
                dialog.dismiss();
            } catch (IllegalArgumentException e) {
                input.setError("请输入六位十六进制颜色，例如 #088BFF");
            }
        });
    }

    private void updateSwatch(TextView view, int color) {
        int opaque = color | 0xFF000000;
        view.setText(hex(opaque));
        view.setBackground(activity.outlinedRect(opaque, activity.strokeColor(), 1, 12));
        double luminance = 0.299 * Color.red(opaque) + 0.587 * Color.green(opaque)
                + 0.114 * Color.blue(opaque);
        view.setTextColor(luminance > 150 ? Color.BLACK : Color.WHITE);
    }

    private static int parseColor(String text) {
        String value = text.trim();
        if (value.startsWith("#")) value = value.substring(1);
        if (!value.matches("[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Invalid RGB");
        return 0xFF000000 | Integer.parseInt(value, 16);
    }

    private static String hex(int color) {
        return String.format(Locale.US, "#%06X", color & 0xFFFFFF);
    }
}
