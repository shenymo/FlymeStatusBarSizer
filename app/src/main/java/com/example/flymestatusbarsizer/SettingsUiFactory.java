package com.example.flymestatusbarsizer;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

final class SettingsUiFactory {
    private final MainActivity activity;

    SettingsUiFactory(MainActivity activity) {
        this.activity = activity;
    }

    TextView chip(String text, int backgroundColor, int textColor) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextColor(textColor);
        view.setTextSize(12);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(12), dp(6), dp(12), dp(6));
        view.setBackground(roundRect(backgroundColor, 99));
        return view;
    }

    TextView filledButton(String text, int backgroundColor, int textColor) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextColor(textColor);
        view.setTextSize(14);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(40));
        view.setPadding(dp(16), dp(8), dp(16), dp(8));
        view.setBackground(roundRect(backgroundColor, 8));
        return view;
    }

    TextView helpButton(String titleText, String message) {
        TextView view = new TextView(activity);
        view.setText("?");
        view.setTextColor(activity.primaryColor());
        view.setTextSize(14);
        view.setGravity(Gravity.CENTER);
        view.setMinWidth(dp(32));
        view.setMinHeight(dp(32));
        view.setContentDescription(titleText + "说明");
        view.setBackground(roundRect(activity.surfaceSoftColor(), 999));
        activity.setTapClickListener(view, v -> activity.showHelpDialog(titleText, message));
        return view;
    }

    GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    GradientDrawable outlinedRect(int color, int strokeColor, int strokeWidthDp, int radiusDp) {
        GradientDrawable drawable = roundRect(color, radiusDp);
        drawable.setStroke(dp(strokeWidthDp), strokeColor);
        return drawable;
    }

    LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    LinearLayout.LayoutParams matchWrapWithTop(int topDp) {
        LinearLayout.LayoutParams lp = matchWrap();
        lp.topMargin = dp(topDp);
        return lp;
    }

    int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    void addDivider(LinearLayout root) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        lp.topMargin = dp(10);
        lp.bottomMargin = dp(10);
        root.addView(buildDividerView(), lp);
    }

    void addProfileSectionHeader(LinearLayout root, String titleText, String subtitleText) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(activity);
        title.setText(titleText);
        title.setTextColor(activity.primaryColor());
        title.setTextSize(14);
        row.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        addHelpButton(row, titleText, subtitleText);
        root.addView(row, matchWrap());
    }

    void addActionButtonRow(LinearLayout root, String titleText, String subtitleText,
            String buttonText, Runnable action) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(56));

        LinearLayout textColumn = new LinearLayout(activity);
        textColumn.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(activity);
        title.setText(titleText);
        title.setTextColor(activity.textColor());
        title.setTextSize(16);
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
    }

    void addHelpButton(LinearLayout row, String titleText, String message) {
        if (message == null || message.length() == 0) {
            return;
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(32), dp(32));
        lp.rightMargin = dp(10);
        row.addView(helpButton(titleText, message), lp);
    }

    private View buildDividerView() {
        View divider = new View(activity);
        int stroke = activity.strokeColor();
        int softDividerColor = Color.argb(0x3B, Color.red(stroke), Color.green(stroke), Color.blue(stroke));
        divider.setBackgroundColor(softDividerColor);
        return divider;
    }
}
