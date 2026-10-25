package com.example.flymestatusbarsizer.ui;

import com.example.flymestatusbarsizer.MainActivity;
import com.example.flymestatusbarsizer.config.SettingsStore;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

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
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(12), dp(4), dp(12), dp(4));
        view.setBackground(roundRect(backgroundColor, 99));
        return view;
    }

    public TextView filledButton(String text, int backgroundColor, int textColor) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextColor(textColor);
        view.setTextSize(14);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(40));
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setPadding(dp(12), dp(6), dp(12), dp(6));
        view.setBackground(roundRect(backgroundColor, 8));
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
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        lp.leftMargin = dp(16);
        lp.rightMargin = dp(16);
        root.addView(buildDividerView(), lp);
    }

    public void addProfileSectionHeader(LinearLayout root, String titleText, String subtitleText) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(activity);
        title.setText(titleText);
        title.setTextColor(activity.subtextColor());
        title.setTextSize(13);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setPadding(dp(16), dp(12), dp(16), dp(4));
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
        row.setMinimumHeight(dp(52));

        LinearLayout textColumn = new LinearLayout(activity);
        textColumn.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(activity);
        title.setText(titleText);
        title.setTextColor(activity.textColor());
        title.setTextSize(15);
        textColumn.addView(title, matchWrap());

        TextView button = filledButton(buttonText, activity.primaryColor(), android.graphics.Color.WHITE);
        activity.setTapClickListener(button, v -> action.run());

        row.addView(textColumn, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        addHelpButton(row, titleText, subtitleText);
        row.addView(button, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(row, matchWrap());
        return button;
    }

    public void addMultiChoiceRow(LinearLayout root, String titleText, String subtitleText,
            String key, int defaultValue, int[] values, String[] labels, String emptyLabel) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setMinimumHeight(dp(52));
        TextView title = new TextView(activity);
        title.setText(titleText);
        title.setTextColor(activity.textColor());
        title.setTextSize(15);
        row.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        addHelpButton(row, titleText, subtitleText);
        TextView summary = new TextView(activity);
        summary.setTextColor(activity.primaryColor());
        summary.setTextSize(13);
        summary.setMaxLines(1);
        summary.setEllipsize(android.text.TextUtils.TruncateAt.END);
        summary.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        summary.setText(multiChoiceLabel(readMultiChoiceValue(key, defaultValue),
                values, labels, emptyLabel));
        summary.setMaxWidth(dp(180));
        activity.setTapClickListener(row, v -> {
            int current = readMultiChoiceValue(key, defaultValue);
            boolean[] checked = new boolean[values.length];
            for (int i = 0; i < values.length; i++) checked[i] = (current & values[i]) != 0;
            AlertDialog dialog = new AlertDialog.Builder(activity)
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
            activity.styleDialog(dialog);
            activity.attachDialogButtonHaptics(dialog);
        });
        row.addView(summary, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        ImageView arrow = new ImageView(activity);
        arrow.setImageResource(com.example.flymestatusbarsizer.R.drawable.ic_section_expand);
        arrow.setRotation(-90f);
        arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams arrowLp = new LinearLayout.LayoutParams(dp(20), dp(20));
        arrowLp.leftMargin = dp(4);
        row.addView(arrow, arrowLp);
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
        ImageView help = new ImageView(activity);
        help.setImageResource(com.example.flymestatusbarsizer.R.drawable.ic_settings_info);
        help.setColorFilter(activity.primaryColor());
        help.setPadding(dp(6), dp(6), dp(6), dp(6));
        help.setBackground(roundRect(Color.TRANSPARENT, 18));
        help.setContentDescription("查看" + titleText + "说明");
        activity.setTapClickListener(help, v -> activity.showHelpDialog(titleText, message));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(36), dp(36));
        lp.leftMargin = dp(4);
        row.addView(help, lp);
    }

    private View buildDividerView() {
        View divider = new View(activity);
        int stroke = activity.strokeColor();
        int softDividerColor = Color.argb(0x3B, Color.red(stroke), Color.green(stroke), Color.blue(stroke));
        divider.setBackgroundColor(softDividerColor);
        return divider;
    }
}
