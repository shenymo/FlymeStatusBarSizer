package com.example.flymestatusbarsizer.feature.battery;

import com.example.flymestatusbarsizer.MainActivity;
import com.example.flymestatusbarsizer.config.SettingsStore;
import com.example.flymestatusbarsizer.ui.ColorPreferenceEditor;

import android.app.AlertDialog;
import android.text.Editable;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

public final class CircleBatteryAppearanceEditor {
    private final MainActivity activity;
    private final ColorPreferenceEditor colors;

    public CircleBatteryAppearanceEditor(MainActivity activity) {
        this.activity = activity;
        colors = new ColorPreferenceEditor(activity);
    }

    public void addTo(LinearLayout root) {
        addTransparency(root);
        activity.addDivider(root);
        colors.addColor(root, "正常 · 浅色背景", SettingsStore.KEY_CAMERA_CIRCLE_BATTERY_NORMAL_LIGHT_COLOR,
                SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_NORMAL_LIGHT_COLOR);
        colors.addColor(root, "正常 · 深色背景", SettingsStore.KEY_CAMERA_CIRCLE_BATTERY_NORMAL_DARK_COLOR,
                SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_NORMAL_DARK_COLOR);
        colors.addColor(root, "充电", SettingsStore.KEY_CAMERA_CIRCLE_BATTERY_CHARGING_COLOR,
                SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_CHARGING_COLOR);
        colors.addColor(root, "省电模式", SettingsStore.KEY_CAMERA_CIRCLE_BATTERY_POWER_SAVE_COLOR,
                SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_POWER_SAVE_COLOR);
        colors.addColor(root, "低电量（低于 10%）", SettingsStore.KEY_CAMERA_CIRCLE_BATTERY_LOW_COLOR,
                SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_LOW_COLOR);
    }

    private void addTransparency(LinearLayout root) {
        String key = SettingsStore.KEY_CAMERA_CIRCLE_BATTERY_TRANSPARENCY_TENTH_PERCENT;
        int defaultValue = SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_TRANSPARENCY_TENTH_PERCENT;
        LinearLayout header = row("电量圆弧透明度");
        activity.addHelpButton(header, "电量圆弧透明度",
                "0% 完全不透明，100% 完全透明。默认 11.8% 透明（88.2% 不透明），只调整电量圆弧；彩虹色也生效。点击数值可精确输入。背景环保持系统原样。");
        TextView value = activity.chip("", activity.surfaceSoftColor(), activity.primaryColor());
        value.setContentDescription("电量圆弧透明度，点击输入");
        header.addView(value);
        root.addView(header);
        SeekBar slider = new SeekBar(activity);
        slider.setContentDescription("电量圆弧透明度");
        activity.styleSeekBar(slider);
        slider.setMax(1000);
        slider.setProgress(Math.max(0, Math.min(1000,
                SettingsStore.readInt(activity.prefs(), key, defaultValue))));
        value.setText(percent(slider.getProgress()));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                value.setText(percent(progress));
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                activity.putIntSetting(key, bar.getProgress());
            }
        });
        root.addView(slider, new LinearLayout.LayoutParams(-1, activity.dp(40)));
        activity.setTapClickListener(value, v -> {
            EditText input = new EditText(activity);
            input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            input.setText(String.format(Locale.US, "%.1f", slider.getProgress() / 10f));
            input.selectAll();
            AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setTitle("电量圆弧透明度")
                    .setMessage("输入 0～100，支持一位小数。数值越大越透明。")
                    .setView(input).setNegativeButton("取消", null)
                    .setNeutralButton("恢复默认", (d, w) -> {
                        slider.setProgress(defaultValue);
                        activity.putIntSetting(key, defaultValue);
                    })
                    .setPositiveButton("应用", null).create();
            show(dialog);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
                try {
                    float number = Float.parseFloat(input.getText().toString().trim());
                    if (!Float.isFinite(number) || number < 0 || number > 100) {
                        throw new NumberFormatException();
                    }
                    int selected = Math.round(number * 10);
                    slider.setProgress(selected);
                    activity.putIntSetting(key, selected);
                    dialog.dismiss();
                } catch (NumberFormatException e) {
                    input.setError("请输入 0～100 的数值");
                }
            });
        });
    }

    private LinearLayout row(String title) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, activity.dp(4), 0, activity.dp(4));
        TextView label = new TextView(activity);
        label.setText(title);
        label.setTextSize(16);
        label.setTextColor(activity.textColor());
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }

    private void show(AlertDialog dialog) {
        dialog.show();
        activity.styleDialog(dialog);
        activity.attachDialogButtonHaptics(dialog);
    }

    private static String percent(int value) {
        return String.format(Locale.US, "%.1f%%", value / 10f);
    }
}
