package com.example.flymestatusbarsizer.ui;

import com.example.flymestatusbarsizer.MainActivity;
import com.example.flymestatusbarsizer.config.SettingsStore;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.divider.MaterialDivider;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;

public final class SettingsUiFactory {
    private final MainActivity activity;

    public SettingsUiFactory(MainActivity activity) {
        this.activity = activity;
    }

    public TextView chip(String text, int backgroundColor, int textColor) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextColor(textColor);
        view.setTextSize(12);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(12), dp(4), dp(12), dp(4));
        view.setBackground(roundRect(backgroundColor, 8));
        return view;
    }

    public MaterialButton filledButton(String text, int backgroundColor, int textColor) {
        MaterialButton view = new MaterialButton(activity, null, com.google.android.material.R.attr.materialButtonStyle);
        view.setText(text);
        view.setTextColor(textColor);
        view.setTextSize(14);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(40));
        view.setCornerRadius(dp(20));
        view.setBackgroundTintList(ColorStateList.valueOf(backgroundColor));
        view.setInsetTop(0);
        view.setInsetBottom(0);
        view.setPadding(dp(16), dp(6), dp(16), dp(6));
        return view;
    }

    public MaterialButton tonalButton(String text, int backgroundColor, int textColor) {
        MaterialButton view = new MaterialButton(activity, null, com.google.android.material.R.attr.materialButtonStyle);
        view.setText(text);
        view.setTextColor(textColor);
        view.setTextSize(13);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(36));
        view.setCornerRadius(dp(18));
        view.setBackgroundTintList(ColorStateList.valueOf(backgroundColor));
        view.setInsetTop(0);
        view.setInsetBottom(0);
        view.setPadding(dp(14), dp(4), dp(14), dp(4));
        return view;
    }

    public GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    public GradientDrawable outlinedRect(int color, int strokeColor, int strokeWidthDp, int radiusDp) {
        GradientDrawable drawable = roundRect(color, radiusDp);
        drawable.setStroke(dp(strokeWidthDp), strokeColor);
        return drawable;
    }

    public LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    public LinearLayout.LayoutParams matchWrapWithTop(int topDp) {
        LinearLayout.LayoutParams lp = matchWrap();
        lp.topMargin = dp(topDp);
        return lp;
    }

    public int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    public void addDivider(LinearLayout root) {
        MaterialDivider divider = new MaterialDivider(activity);
        divider.setDividerColor(activity.surfaceStrongColor());
        divider.setDividerInsetStart(dp(16));
        divider.setDividerInsetEnd(dp(16));
        divider.setDividerThickness(dp(1));
        root.addView(divider, matchWrap());
    }

    public void addProfileSectionHeader(LinearLayout root, String titleText, String subtitleText) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(activity);
        title.setText(titleText);
        title.setTextColor(activity.primaryColor());
        title.setTextSize(13);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setPadding(dp(16), dp(14), dp(16), dp(6));
        row.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        addHelpButton(row, titleText, subtitleText);
        root.addView(row, matchWrap());
    }

    public TextView addActionButtonRow(LinearLayout root, String titleText, String subtitleText,
            String buttonText, Runnable action) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(56));
        row.setPadding(0, dp(4), 0, dp(4));

        LinearLayout textColumn = new LinearLayout(activity);
        textColumn.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(activity);
        title.setText(titleText);
        title.setTextColor(activity.textColor());
        title.setTextSize(15);
        title.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        textColumn.addView(title, matchWrap());

        if (subtitleText != null && !subtitleText.trim().isEmpty()) {
            TextView sub = new TextView(activity);
            sub.setText(subtitleText);
            sub.setTextColor(activity.subtextColor());
            sub.setTextSize(12);
            textColumn.addView(sub, matchWrapWithTop(2));
        }

        MaterialButton button = filledButton(buttonText, activity.primaryColor(), Color.WHITE);
        activity.setTapClickListener(button, v -> action.run());

        row.addView(textColumn, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        addHelpButton(row, titleText, subtitleText);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.leftMargin = dp(12);
        row.addView(button, btnLp);
        root.addView(row, matchWrap());
        return button;
    }

    public void addMultiChoiceRow(LinearLayout root, String titleText, String subtitleText,
            String key, int defaultValue, int[] values, String[] labels, String emptyLabel) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setMinimumHeight(dp(56));
        row.setPadding(0, dp(4), 0, dp(4));

        LinearLayout textColumn = new LinearLayout(activity);
        textColumn.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(activity);
        title.setText(titleText);
        title.setTextColor(activity.textColor());
        title.setTextSize(15);
        textColumn.addView(title, matchWrap());

        if (subtitleText != null && !subtitleText.trim().isEmpty()) {
            TextView sub = new TextView(activity);
            sub.setText(subtitleText);
            sub.setTextColor(activity.subtextColor());
            sub.setTextSize(12);
            textColumn.addView(sub, matchWrapWithTop(2));
        }

        row.addView(textColumn, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        addHelpButton(row, titleText, subtitleText);

        TextView summary = new TextView(activity);
        summary.setTextColor(activity.primaryColor());
        summary.setTextSize(13);
        summary.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        summary.setPadding(dp(12), dp(4), dp(12), dp(4));
        summary.setBackground(roundRect(activity.surfaceStrongColor(), 8));
        summary.setMinHeight(dp(32));
        summary.setMaxLines(1);
        summary.setEllipsize(android.text.TextUtils.TruncateAt.END);
        summary.setGravity(Gravity.CENTER);
        summary.setText(multiChoiceLabel(readMultiChoiceValue(key, defaultValue),
                values, labels, emptyLabel));
        summary.setMaxWidth(dp(180));
        activity.setTapClickListener(row, v -> {
            int current = readMultiChoiceValue(key, defaultValue);
            boolean[] checked = new boolean[values.length];
            for (int i = 0; i < values.length; i++) checked[i] = (current & values[i]) != 0;
            AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                    .setTitle(titleText)
                    .setMultiChoiceItems(labels, checked, (d, which, selected) -> checked[which] = selected)
                    .setNegativeButton("取消", null)
                    .setPositiveButton("确定", (d, which) -> {
                        int selected = 0;
                        for (int i = 0; i < values.length; i++) if (checked[i]) selected |= values[i];
                        activity.putIntSetting(key, selected);
                        summary.setText(multiChoiceLabel(selected, values, labels, emptyLabel));
                    })
                    .show();
            activity.attachDialogButtonHaptics(dialog);
        });
        row.addView(summary, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(row, matchWrap());
    }

    private int readMultiChoiceValue(String key, int defaultValue) {
        int value = SettingsStore.readInt(activity.prefs(), key, defaultValue);
        return value < 0 ? defaultValue : value;
    }

    private static String multiChoiceLabel(int selected, int[] values, String[] labels, String emptyLabel) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if ((selected & values[i]) == 0) continue;
            if (result.length() > 0) result.append("、");
            result.append(labels[i]);
        }
        return result.length() == 0 ? emptyLabel : result.toString();
    }

    public void addHelpButton(LinearLayout row, String titleText, String message) {
        activity.addSearchItem(row, titleText, message);
        if (message == null || message.trim().isEmpty()) {
            return;
        }
        row.setContentDescription(titleText + "，长按查看说明");
        row.setOnLongClickListener(v -> {
            activity.performTapHaptic(v);
            activity.showHelpDialog(titleText, message);
            return true;
        });
    }
}
