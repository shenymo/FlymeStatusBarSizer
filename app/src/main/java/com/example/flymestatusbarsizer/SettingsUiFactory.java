package com.example.flymestatusbarsizer;

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
        view.setPadding(dp(14), dp(10), dp(14), dp(10));
        view.setBackground(roundRect(backgroundColor, 999));
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
        lp.topMargin = dp(14);
        lp.bottomMargin = dp(14);
        root.addView(buildDividerView(), lp);
    }

    void addProfileSectionHeader(LinearLayout root, String titleText, String subtitleText) {
        TextView title = new TextView(activity);
        title.setText(titleText);
        title.setTextColor(activity.primaryColor());
        title.setTextSize(15);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(activity);
        subtitle.setText(subtitleText);
        subtitle.setTextColor(activity.subtextColor());
        subtitle.setTextSize(12);
        subtitle.setPadding(0, dp(4), 0, 0);
        root.addView(subtitle, matchWrapWithTop(2));
    }

    void addActionButtonRow(LinearLayout root, String titleText, String subtitleText,
            String buttonText, Runnable action) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout textColumn = new LinearLayout(activity);
        textColumn.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(activity);
        title.setText(titleText);
        title.setTextColor(activity.textColor());
        title.setTextSize(16);
        textColumn.addView(title, matchWrap());

        TextView subtitle = new TextView(activity);
        subtitle.setText(subtitleText);
        subtitle.setTextColor(activity.subtextColor());
        subtitle.setTextSize(13);
        subtitle.setPadding(0, dp(4), dp(10), 0);
        textColumn.addView(subtitle, matchWrap());

        TextView button = filledButton(buttonText, activity.primaryColor(), android.graphics.Color.WHITE);
        activity.setTapClickListener(button, v -> action.run());

        row.addView(textColumn, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(button, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(row, matchWrap());
    }

    private View buildDividerView() {
        View divider = new View(activity);
        divider.setBackgroundColor(activity.strokeColor());
        return divider;
    }
}
