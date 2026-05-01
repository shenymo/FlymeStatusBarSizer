package com.example.flymestatusbarsizer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
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

    private static final int MENU_IMPORT = 1;
    private static final int MENU_EXPORT = 2;
    private static final int MENU_RESET = 3;
    private static final int MENU_RESTART = 4;

    private static final int FALLBACK_BACKGROUND = Color.rgb(253, 248, 253);
    private static final int FALLBACK_SURFACE = Color.WHITE;
    private static final int FALLBACK_SURFACE_SOFT = Color.rgb(241, 236, 242);
    private static final int FALLBACK_SURFACE_STRONG = Color.rgb(232, 222, 249);
    private static final int FALLBACK_FEATURE_SURFACE = Color.rgb(245, 238, 255);
    private static final int FALLBACK_FEATURE_STROKE = Color.rgb(207, 188, 255);
    private static final int FALLBACK_TEXT = Color.rgb(28, 27, 31);
    private static final int FALLBACK_SUBTEXT = Color.rgb(73, 69, 81);
    private static final int FALLBACK_PRIMARY = Color.rgb(79, 55, 138);
    private static final int FALLBACK_PRIMARY_CONTAINER = Color.rgb(103, 80, 164);
    private static final int FALLBACK_PRIMARY_DEEP = Color.rgb(58, 43, 103);
    private static final int FALLBACK_STROKE = Color.rgb(203, 196, 210);

    private SharedPreferences prefs;
    private int colorBackground;
    private int colorSurface;
    private int colorSurfaceSoft;
    private int colorSurfaceStrong;
    private int colorFeatureSurface;
    private int colorFeatureStroke;
    private int colorText;
    private int colorSubtext;
    private int colorPrimary;
    private int colorPrimaryContainer;
    private int colorPrimaryDeep;
    private int colorStroke;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = SettingsStore.prefs(this);
        initPalette();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(true);
        }

        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(colorBackground);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(colorBackground);
        scrollView.setFillViewport(true);
        page.addView(scrollView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(28));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        root.addView(buildTopBar(), matchWrap());
        root.addView(buildPreviewPlaceholder(), matchWrapWithTop(18));
        root.addView(buildIntroCard(), matchWrapWithTop(16));

        addSectionLabel(root, "\u5168\u5c40\u8c03\u6574");
        root.addView(buildGlobalCard(), matchWrapWithTop(10));

        addSectionLabel(root, "\u56fe\u6807\u91cd\u7ed8");
        root.addView(buildBatterySection(), matchWrapWithTop(10));
        root.addView(buildSignalSection(), matchWrapWithTop(12));
        root.addView(buildNetworkTypeSection(), matchWrapWithTop(12));
        root.addView(buildWifiSection(), matchWrapWithTop(12));

        addSectionLabel(root, "\u65f6\u95f4\u6587\u5b57");
        root.addView(buildTimeCard(), matchWrapWithTop(10));

        page.addView(buildFloatingMenuButton(), floatingMenuLayoutParams());
        setContentView(page);
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

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);

        TextView eyebrow = new TextView(this);
        eyebrow.setText("Status Bar Lab");
        eyebrow.setTextColor(colorPrimary);
        eyebrow.setTextSize(12);
        left.addView(eyebrow, matchWrap());

        TextView title = new TextView(this);
        title.setText("Flyme \u72b6\u6001\u680f");
        title.setTextColor(colorText);
        title.setTextSize(28);
        left.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("\u4e3b\u9875\u76f4\u63a5\u8c03\u6574 iOS \u91cd\u7ed8\u3001\u7f29\u653e\u548c\u4f4d\u7f6e");
        subtitle.setTextColor(colorSubtext);
        subtitle.setTextSize(14);
        subtitle.setPadding(0, dp(4), 0, 0);
        left.addView(subtitle, matchWrap());
        bar.addView(left, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return bar;
    }

    private View buildFloatingMenuButton() {
        TextView button = new TextView(this);
        button.setText("\u83dc\u5355");
        button.setTextColor(Color.WHITE);
        button.setTextSize(14);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(18), dp(12), dp(18), dp(12));
        button.setBackground(roundRect(colorPrimary, 999));
        button.setElevation(dp(6));
        button.setOnClickListener(this::showTopMenu);
        return button;
    }

    private View buildPreviewPlaceholder() {
        FrameLayout card = new FrameLayout(this);
        card.setMinimumHeight(dp(220));
        card.setPadding(dp(24), dp(24), dp(24), dp(24));
        card.setBackground(gradientCard());

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        card.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        TextView title = new TextView(this);
        title.setText("\u9884\u89c8\u533a");
        title.setTextColor(Color.argb(235, 255, 255, 255));
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER);
        content.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("\u6682\u65f6\u4fdd\u7559\u4e3a\u5360\u4f4d\uff0c\u540e\u7eed\u53ef\u4ee5\u63a5\u5165\u72b6\u6001\u680f\u5b9e\u65f6\u9884\u89c8");
        subtitle.setTextColor(Color.argb(210, 255, 255, 255));
        subtitle.setTextSize(14);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(dp(16), dp(10), dp(16), 0);
        content.addView(subtitle, matchWrap());

        TextView badge = chip("\u5f85\u5b9e\u88c5", Color.argb(55, 255, 255, 255), Color.WHITE);
        FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        badgeLp.gravity = Gravity.TOP | Gravity.START;
        card.addView(badge, badgeLp);
        return card;
    }

    private View buildIntroCard() {
        LinearLayout card = card(colorSurface, 24);

        TextView title = new TextView(this);
        title.setText("\u4f7f\u7528\u8bf4\u660e");
        title.setTextColor(colorText);
        title.setTextSize(18);
        card.addView(title, matchWrap());

        TextView summary = new TextView(this);
        summary.setText("\u8bbe\u7f6e\u4f1a\u76f4\u63a5\u5199\u5165\u6a21\u5757\u914d\u7f6e\uff0cSystemUI \u91cd\u542f\u540e\u751f\u6548\u66f4\u7a33\u5b9a\u3002\u4e2d\u95f4\u7684\u5361\u7247\u53ef\u4ee5\u70b9\u51fb\u5c55\u5f00\uff0c\u5bf9\u5e94\u56fe\u6807\u7684\u7f29\u653e\u548c\u4f4d\u7f6e\u90fd\u5728\u91cc\u9762\u3002");
        summary.setTextColor(colorSubtext);
        summary.setTextSize(14);
        summary.setPadding(0, dp(6), 0, 0);
        card.addView(summary, matchWrap());
        return card;
    }

    private View buildGlobalCard() {
        LinearLayout card = card(colorSurface, 28);
        addSliderRow(card, "\u72b6\u6001\u680f\u6574\u4f53\u56fe\u6807\u7f29\u653e",
                "\u79fb\u52a8\u4fe1\u53f7\u3001Wi-Fi\u3001\u7535\u6c60\u548c\u5176\u4ed6\u5c0f\u56fe\u6807\u4e00\u8d77\u8c03\u6574",
                SettingsStore.KEY_GLOBAL_ICON_SCALE, SettingsStore.DEFAULT_GLOBAL_ICON_SCALE, 80, 160, "%");
        addDivider(card);
        addSliderRow(card, "\u6587\u5b57\u5927\u5c0f",
                "\u65f6\u949f\u3001\u8fd0\u8425\u5546\u3001\u7535\u6c60\u767e\u5206\u6bd4\u7b49\u72b6\u6001\u680f\u6587\u5b57\u7edf\u4e00\u7f29\u653e",
                SettingsStore.KEY_TEXT_SCALE, SettingsStore.DEFAULT_TEXT_SCALE, 80, 130, "%");
        return card;
    }

    private View buildBatterySection() {
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        addSliderRow(details, "\u7535\u6c60\u7f29\u653e\u5f3a\u5ea6",
                "FlymeBatteryMeterView \u8ddf\u968f\u6574\u4f53\u7f29\u653e\u7684\u5f3a\u5ea6",
                SettingsStore.KEY_BATTERY_FACTOR, SettingsStore.DEFAULT_BATTERY_FACTOR, 0, 160, "%");
        addDivider(details);
        addSliderRow(details, "\u7535\u6c60\u957f\u5ea6",
                "\u5305\u542b\u7535\u6c60\u5934\u7684\u6574\u4f53\u7ed8\u5236\u5bbd\u5ea6",
                SettingsStore.KEY_IOS_BATTERY_WIDTH, SettingsStore.DEFAULT_IOS_BATTERY_WIDTH, 16, 44, "dp");
        addDivider(details);
        addSliderRow(details, "\u7535\u6c60\u9ad8\u5ea6",
                "\u7535\u6c60\u4e3b\u4f53\u7684\u9ad8\u5ea6",
                SettingsStore.KEY_IOS_BATTERY_HEIGHT, SettingsStore.DEFAULT_IOS_BATTERY_HEIGHT, 8, 24, "dp");
        addDivider(details);
        addSliderRow(details, "\u5de6\u53f3\u504f\u79fb",
                "\u6b63\u6570\u5411\u53f3\uff0c\u8d1f\u6570\u5411\u5de6",
                SettingsStore.KEY_IOS_BATTERY_OFFSET_X, SettingsStore.DEFAULT_IOS_BATTERY_OFFSET_X, -20, 20, "dp");
        addDivider(details);
        addSliderRow(details, "\u4e0a\u4e0b\u504f\u79fb",
                "\u6b63\u6570\u5411\u4e0b\uff0c\u8d1f\u6570\u5411\u4e0a",
                SettingsStore.KEY_IOS_BATTERY_OFFSET_Y, SettingsStore.DEFAULT_IOS_BATTERY_OFFSET_Y, -20, 20, "dp");
        addDivider(details);
        addSliderRow(details, "\u5185\u90e8\u5b57\u4f53\u5927\u5c0f",
                "\u7535\u91cf\u6570\u5b57\u5728\u7535\u6c60\u5185\u7684\u663e\u793a\u6bd4\u4f8b",
                SettingsStore.KEY_IOS_BATTERY_TEXT_SIZE, SettingsStore.DEFAULT_IOS_BATTERY_TEXT_SIZE, 40, 100, "%");
        return buildExpandableFeatureCard(
                "\u7535\u6c60",
                "\u53ef\u4ee5\u5207\u6362 iOS \u98ce\u683c\u7535\u6c60\uff0c\u5e76\u8c03\u6574\u5c3a\u5bf8\u3001\u504f\u79fb\u548c\u5185\u90e8\u6570\u5b57",
                FeatureToggle.single(SettingsStore.KEY_IOS_BATTERY_STYLE, SettingsStore.DEFAULT_IOS_BATTERY_STYLE),
                "\u7535\u6c60", details);
    }

    private View buildSignalSection() {
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        addSwitchRow(details, "\u53cc\u5361\u5408\u4e00\u4fe1\u53f7\u683c",
                "\u5361 1 \u4f5c\u4e3a\u4e3b\u4fe1\u53f7\u683c\uff0c\u5361 2 \u5408\u5e76\u5230\u4e0b\u65b9\u56db\u4e2a\u5c0f\u70b9",
                SettingsStore.KEY_IOS_SIGNAL_DUAL_COMBINED, SettingsStore.DEFAULT_IOS_SIGNAL_DUAL_COMBINED);
        addDivider(details);
        addProfileSectionHeader(details, "\u5f00\u542f\u65f6\u914d\u7f6e",
                "\u603b\u5f00\u5173\u6253\u5f00\u540e\uff0c\u4fe1\u53f7\u683c\u4f7f\u7528\u8fd9\u7ec4\u7f29\u653e\u548c\u504f\u79fb");
        addSignalProfileRows(details, true);
        addDivider(details);
        addProfileSectionHeader(details, "\u5173\u95ed\u65f6\u914d\u7f6e",
                "\u603b\u5f00\u5173\u5173\u95ed\u540e\uff0c\u539f\u751f\u4fe1\u53f7\u683c\u4f7f\u7528\u8fd9\u7ec4\u7f29\u653e\u548c\u504f\u79fb");
        addSignalProfileRows(details, false);
        addDivider(details);
        addLongPressResetOffsetsRow(details, "\u6062\u590d", "\u957f\u6309\u540e\u628a\u5f00\u542f/\u5173\u95ed\u4e24\u5957\u914d\u7f6e\u91cc\u7684\u4fe1\u53f7\u683c\u548c 5G \u6807\u8bc6\u504f\u79fb\u5168\u90e8\u6062\u590d\u4e3a 0");
        return buildExpandableFeatureCard(
                "\u4fe1\u53f7\u683c",
                "\u8fd9\u4e2a\u603b\u5f00\u5173\u4f1a\u540c\u65f6\u63a7\u5236 iOS \u4fe1\u53f7\u683c\u548c 5G \u6807\u8bc6\uff0c\u5f00\u542f\u548c\u5173\u95ed\u4f1a\u5207\u6362\u4e0b\u9762\u5404\u81ea\u7684\u4e24\u5957\u914d\u7f6e",
                FeatureToggle.createLinked(
                        SettingsStore.KEY_IOS_SIGNAL_STYLE,
                        SettingsStore.DEFAULT_IOS_SIGNAL_STYLE,
                        SettingsStore.KEY_IOS_NETWORK_TYPE_STYLE,
                        SettingsStore.DEFAULT_IOS_NETWORK_TYPE_STYLE),
                "Signal", details);
    }

    private View buildNetworkTypeSection() {
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        addProfileSectionHeader(details, "\u5f00\u542f\u65f6\u914d\u7f6e",
                "\u603b\u5f00\u5173\u6253\u5f00\u540e\uff0c5G \u6807\u8bc6\u4f7f\u7528\u8fd9\u7ec4\u7f29\u653e\u548c\u504f\u79fb");
        addNetworkTypeProfileRows(details, true);
        addDivider(details);
        addProfileSectionHeader(details, "\u5173\u95ed\u65f6\u914d\u7f6e",
                "\u603b\u5f00\u5173\u5173\u95ed\u540e\uff0c\u539f\u751f 5G/\u7f51\u7edc\u7c7b\u578b\u56fe\u6807\u4f7f\u7528\u8fd9\u7ec4\u7f29\u653e\u548c\u504f\u79fb");
        addNetworkTypeProfileRows(details, false);
        addDivider(details);
        addLongPressResetOffsetsRow(details, "\u6062\u590d", "\u957f\u6309\u540e\u628a\u5f00\u542f/\u5173\u95ed\u4e24\u5957\u914d\u7f6e\u91cc\u7684\u4fe1\u53f7\u683c\u548c 5G \u6807\u8bc6\u504f\u79fb\u5168\u90e8\u6062\u590d\u4e3a 0");
        return buildExpandableFeatureCard(
                "5G \u6807\u8bc6",
                "\u5f00\u5173\u8ddf\u968f\u4e0a\u9762\u7684\u4fe1\u53f7\u683c\u603b\u5f00\u5173\uff0c\u5f00\u542f\u548c\u5173\u95ed\u65f6\u4f1a\u81ea\u52a8\u5207\u6362\u5bf9\u5e94\u7684 5G \u914d\u7f6e",
                FeatureToggle.readOnlyLinked(
                        SettingsStore.KEY_IOS_SIGNAL_STYLE,
                        SettingsStore.DEFAULT_IOS_SIGNAL_STYLE,
                        SettingsStore.KEY_IOS_NETWORK_TYPE_STYLE,
                        SettingsStore.DEFAULT_IOS_NETWORK_TYPE_STYLE),
                "5G", details);
    }

    private View buildWifiSection() {
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        addSliderRow(details, "Wi-Fi \u7f29\u653e\u5f3a\u5ea6",
                "wifi_signal \u56fe\u6807\u7684\u4e13\u5c5e\u7f29\u653e",
                SettingsStore.KEY_WIFI_SIGNAL_FACTOR, SettingsStore.DEFAULT_WIFI_SIGNAL_FACTOR, 0, 160, "%");
        addDivider(details);
        addSliderRow(details, "Wi-Fi \u5bbd\u5ea6",
                "\u81ea\u7ed8 Wi-Fi \u5bb9\u5668\u7684\u5e03\u5c40\u5bbd\u5ea6",
                SettingsStore.KEY_IOS_WIFI_WIDTH, SettingsStore.DEFAULT_IOS_WIFI_WIDTH, 10, 60, "dp");
        addDivider(details);
        addSliderRow(details, "Wi-Fi \u9ad8\u5ea6",
                "\u81ea\u7ed8 Wi-Fi \u5bb9\u5668\u7684\u5e03\u5c40\u9ad8\u5ea6",
                SettingsStore.KEY_IOS_WIFI_HEIGHT, SettingsStore.DEFAULT_IOS_WIFI_HEIGHT, 8, 60, "dp");
        addDivider(details);
        addSliderRow(details, "Wi-Fi \u6c34\u5e73\u504f\u79fb",
                "\u6b63\u6570\u5411\u53f3\uff0c\u8d1f\u6570\u5411\u5de6",
                SettingsStore.KEY_IOS_WIFI_OFFSET_X, SettingsStore.DEFAULT_IOS_WIFI_OFFSET_X, -80, 80, "dp");
        addDivider(details);
        addSliderRow(details, "Wi-Fi \u5782\u76f4\u504f\u79fb",
                "\u6b63\u6570\u5411\u4e0b\uff0c\u8d1f\u6570\u5411\u4e0a",
                SettingsStore.KEY_IOS_WIFI_OFFSET_Y, SettingsStore.DEFAULT_IOS_WIFI_OFFSET_Y, -80, 80, "dp");
        addDivider(details);
        addSliderRow(details, "Wi-Fi \u53f3\u4fa7\u95f4\u8ddd",
                "\u8c03\u6574 Wi-Fi \u548c\u53f3\u4fa7\u56fe\u6807\u7684\u5e03\u5c40\u8ddd\u79bb",
                SettingsStore.KEY_IOS_WIFI_MARGIN_END, SettingsStore.DEFAULT_IOS_WIFI_MARGIN_END, -80, 80, "dp");
        return buildExpandableFeatureCard(
                "Wi-Fi",
                "\u6839\u636e SystemUI \u4e0b\u53d1\u7684 wifi_signal \u8d44\u6e90\u52a8\u6001\u7ed8\u5236 Wi-Fi \u5f3a\u5ea6",
                FeatureToggle.single(SettingsStore.KEY_IOS_WIFI_STYLE, SettingsStore.DEFAULT_IOS_WIFI_STYLE),
                "Wi-Fi", details);
    }

    private View buildTimeCard() {
        LinearLayout card = card(colorSurface, 28);
        addSwitchRow(card, "\u65f6\u95f4\u663e\u793a\u661f\u671f",
                "\u5728\u72b6\u6001\u680f\u65f6\u95f4\u53f3\u4fa7\u8ffd\u52a0\u5f53\u524d\u661f\u671f",
                SettingsStore.KEY_SHOW_CLOCK_WEEKDAY, SettingsStore.DEFAULT_SHOW_CLOCK_WEEKDAY);
        addDivider(card);
        addSwitchRow(card, "\u65f6\u95f4\u52a0\u7c97",
                "\u5bf9\u72b6\u6001\u680f\u65f6\u95f4\u4ee5\u53ca\u5176\u53f3\u4fa7\u8ffd\u52a0\u7684\u661f\u671f/\u65e5\u671f\u5e94\u7528\u5b57\u91cd",
                SettingsStore.KEY_CLOCK_BOLD_ENABLED, SettingsStore.DEFAULT_CLOCK_BOLD_ENABLED);
        addDivider(card);
        addSliderRow(card, "\u65f6\u95f4/\u65e5\u671f\u7c97\u7ec6",
                "\u53ea\u5bf9\u72b6\u6001\u680f\u65f6\u95f4\u6587\u5b57\u751f\u6548\uff0c\u8303\u56f4 100-900",
                SettingsStore.KEY_CLOCK_FONT_WEIGHT, SettingsStore.DEFAULT_CLOCK_FONT_WEIGHT, 100, 900, "");
        return card;
    }

    private LinearLayout buildExpandableFeatureCard(String titleText, String subtitleText, FeatureToggle toggleConfig,
            String badgeText, LinearLayout details) {
        LinearLayout card = card(colorFeatureSurface, colorFeatureStroke, 28);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);

        TextView badge = chip(badgeText, colorSurfaceStrong, colorPrimary);
        textColumn.addView(badge, matchWrap());

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(colorText);
        title.setTextSize(20);
        title.setPadding(0, dp(10), 0, 0);
        textColumn.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText(subtitleText);
        subtitle.setTextColor(colorSubtext);
        subtitle.setTextSize(14);
        subtitle.setPadding(0, dp(6), 0, 0);
        textColumn.addView(subtitle, matchWrap());

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

        if (!toggleConfig.readOnly) {
            Switch toggle = new Switch(this);
            toggle.setChecked(readFeatureToggle(toggleConfig));
            toggle.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) ->
                    putFeatureToggle(toggleConfig, isChecked));
            controls.addView(toggle, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        } else {
            TextView followTag = chip("\u8ddf\u968f\u4fe1\u53f7\u683c", colorSurfaceStrong, colorPrimary);
            controls.addView(followTag, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        TextView expand = new TextView(this);
        expand.setText("\u5c55\u5f00");
        expand.setTextColor(colorPrimary);
        expand.setTextSize(13);
        expand.setPadding(0, dp(6), 0, 0);
        controls.addView(expand, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        header.addView(textColumn, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(controls, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        details.setVisibility(View.GONE);
        details.setPadding(0, dp(16), 0, 0);

        header.setOnClickListener(v -> {
            boolean expanded = details.getVisibility() == View.VISIBLE;
            details.setVisibility(expanded ? View.GONE : View.VISIBLE);
            expand.setText(expanded ? "\u5c55\u5f00" : "\u6536\u8d77");
        });

        card.addView(header, matchWrap());
        card.addView(details, matchWrap());
        return card;
    }

    private void addSectionLabel(LinearLayout root, String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(colorPrimary);
        label.setTextSize(13);
        label.setPadding(dp(2), dp(20), 0, 0);
        root.addView(label, matchWrap());
    }

    private void addSwitchRow(LinearLayout root, String titleText, String subtitleText,
            String key, boolean defaultValue) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(colorText);
        title.setTextSize(16);
        textColumn.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText(subtitleText);
        subtitle.setTextColor(colorSubtext);
        subtitle.setTextSize(13);
        subtitle.setPadding(0, dp(4), dp(10), 0);
        textColumn.addView(subtitle, matchWrap());

        Switch toggle = new Switch(this);
        toggle.setChecked(prefs.getBoolean(key, defaultValue));
        toggle.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) ->
                putBooleanSetting(key, isChecked));

        row.addView(textColumn, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(toggle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(row, matchWrap());
    }

    private void addSliderRow(LinearLayout root, String titleText, String subtitleText, String key,
            int defaultValue, int min, int max, String suffix) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(colorText);
        title.setTextSize(16);
        header.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView valueView = new TextView(this);
        valueView.setTextColor(colorPrimary);
        valueView.setTextSize(14);
        valueView.setPadding(dp(12), 0, 0, 0);
        int current = readIntSetting(key, defaultValue);
        int clamped = Math.max(min, Math.min(max, current));
        valueView.setText(formatValue(clamped, suffix));
        valueView.setPadding(dp(12), dp(8), dp(12), dp(8));
        valueView.setBackground(roundRect(colorSurfaceSoft, 999));
        header.addView(valueView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView subtitle = new TextView(this);
        subtitle.setText(subtitleText);
        subtitle.setTextColor(colorSubtext);
        subtitle.setTextSize(13);
        subtitle.setPadding(0, dp(4), 0, 0);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(max - min);
        seekBar.setProgress(clamped - min);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = min + progress;
                valueView.setText(formatValue(value, suffix));
                if (fromUser) {
                    putIntSetting(key, value);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                putIntSetting(key, min + seekBar.getProgress());
            }
        });
        valueView.setOnClickListener(v -> showIntInputDialog(
                titleText,
                min + seekBar.getProgress(),
                min,
                max,
                suffix,
                value -> {
                    valueView.setText(formatValue(value, suffix));
                    seekBar.setProgress(value - min);
                    putIntSetting(key, value);
                }));

        row.addView(header, matchWrap());
        row.addView(subtitle, matchWrap());
        row.addView(seekBar, matchWrapWithTop(8));
        root.addView(row, matchWrap());
    }

    private void addSliderRowWithFallback(LinearLayout root, String titleText, String subtitleText, String key,
            int defaultValue, String fallbackKey, int fallbackDefaultValue, int min, int max, String suffix) {
        int initialValue = getIntValueWithFallback(key, defaultValue, fallbackKey, fallbackDefaultValue);
        addSliderRow(root, titleText, subtitleText, key, initialValue, min, max, suffix);
    }

    private void addOffsetSliderWithFallback(LinearLayout root, String titleText, String subtitleText,
            String key, int defaultValue, String fallbackKey, int fallbackDefaultValue) {
        addSliderRowWithFallback(root, titleText, subtitleText + "\u3002\u70b9\u51fb\u53f3\u4fa7\u6570\u503c\u53ef\u624b\u52a8\u8f93\u5165",
                key, defaultValue,
                fallbackKey, fallbackDefaultValue, -20, 20, "dp");
    }

    private void addDivider(LinearLayout root) {
        View divider = new View(this);
        divider.setBackgroundColor(colorStroke);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        lp.topMargin = dp(14);
        lp.bottomMargin = dp(14);
        root.addView(divider, lp);
    }

    private void addLongPressResetOffsetsRow(LinearLayout root, String titleText, String subtitleText) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(colorPrimary);
        title.setTextSize(16);
        textColumn.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText(subtitleText);
        subtitle.setTextColor(colorSubtext);
        subtitle.setTextSize(13);
        subtitle.setPadding(0, dp(4), dp(10), 0);
        textColumn.addView(subtitle, matchWrap());

        TextView button = chip("\u957f\u6309", colorSurfaceStrong, colorPrimary);
        button.setOnLongClickListener(v -> {
            resetSignalAndNetworkOffsets();
            return true;
        });

        row.addView(textColumn, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(button, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(row, matchWrap());
    }

    private void addProfileSectionHeader(LinearLayout root, String titleText, String subtitleText) {
        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(colorPrimary);
        title.setTextSize(15);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText(subtitleText);
        subtitle.setTextColor(colorSubtext);
        subtitle.setTextSize(12);
        subtitle.setPadding(0, dp(4), 0, 0);
        root.addView(subtitle, matchWrapWithTop(2));
    }

    private void addSignalProfileRows(LinearLayout root, boolean enabledProfile) {
        String factorKey = enabledProfile
                ? SettingsStore.KEY_MOBILE_SIGNAL_FACTOR
                : SettingsStore.KEY_MOBILE_SIGNAL_FACTOR_OFF;
        int factorDefault = enabledProfile
                ? SettingsStore.DEFAULT_MOBILE_SIGNAL_FACTOR
                : SettingsStore.DEFAULT_MOBILE_SIGNAL_FACTOR_OFF;
        String factorFallbackKey = SettingsStore.KEY_MOBILE_SIGNAL_FACTOR;
        int factorFallbackDefault = SettingsStore.DEFAULT_MOBILE_SIGNAL_FACTOR;
        addSliderRowWithFallback(root, "\u4fe1\u53f7\u683c\u7f29\u653e\u5f3a\u5ea6",
                "mobile_signal \u56fe\u6807\u7684\u4e13\u5c5e\u7f29\u653e",
                factorKey, factorDefault, factorFallbackKey, factorFallbackDefault, 0, 160, "%");
        addDivider(root);
        addOffsetSliderWithFallback(root, "\u684c\u9762\u5de6\u53f3\u504f\u79fb",
                "\u684c\u9762\u72b6\u6001\u680f\u4e0b\u7684\u4fe1\u53f7\u683c\u4f4d\u7f6e\u8c03\u6574\u3002\u6b63\u6570\u5411\u53f3\uff0c\u8d1f\u6570\u5411\u5de6",
                enabledProfile ? SettingsStore.KEY_IOS_SIGNAL_DESKTOP_OFFSET_X : SettingsStore.KEY_IOS_SIGNAL_DESKTOP_OFFSET_X_OFF,
                enabledProfile ? SettingsStore.DEFAULT_IOS_SIGNAL_DESKTOP_OFFSET_X : SettingsStore.DEFAULT_IOS_SIGNAL_DESKTOP_OFFSET_X_OFF,
                SettingsStore.KEY_IOS_SIGNAL_DESKTOP_OFFSET_X, SettingsStore.DEFAULT_IOS_SIGNAL_DESKTOP_OFFSET_X);
        addDivider(root);
        addOffsetSliderWithFallback(root, "\u684c\u9762\u4e0a\u4e0b\u504f\u79fb",
                "\u684c\u9762\u72b6\u6001\u680f\u4e0b\u7684\u4fe1\u53f7\u683c\u9ad8\u5ea6\u8c03\u6574\u3002\u6b63\u6570\u5411\u4e0b\uff0c\u8d1f\u6570\u5411\u4e0a",
                enabledProfile ? SettingsStore.KEY_IOS_SIGNAL_DESKTOP_OFFSET_Y : SettingsStore.KEY_IOS_SIGNAL_DESKTOP_OFFSET_Y_OFF,
                enabledProfile ? SettingsStore.DEFAULT_IOS_SIGNAL_DESKTOP_OFFSET_Y : SettingsStore.DEFAULT_IOS_SIGNAL_DESKTOP_OFFSET_Y_OFF,
                SettingsStore.KEY_IOS_SIGNAL_DESKTOP_OFFSET_Y, SettingsStore.DEFAULT_IOS_SIGNAL_DESKTOP_OFFSET_Y);
        addDivider(root);
        addOffsetSliderWithFallback(root, "\u9501\u5c4f\u5de6\u53f3\u504f\u79fb",
                "\u9501\u5c4f\u53f3\u4e0a\u89d2\u4fe1\u53f7\u683c\u7684\u4f4d\u7f6e\u8c03\u6574\u3002\u6b63\u6570\u5411\u53f3\uff0c\u8d1f\u6570\u5411\u5de6",
                enabledProfile ? SettingsStore.KEY_IOS_SIGNAL_KEYGUARD_OFFSET_X : SettingsStore.KEY_IOS_SIGNAL_KEYGUARD_OFFSET_X_OFF,
                enabledProfile ? SettingsStore.DEFAULT_IOS_SIGNAL_KEYGUARD_OFFSET_X : SettingsStore.DEFAULT_IOS_SIGNAL_KEYGUARD_OFFSET_X_OFF,
                SettingsStore.KEY_IOS_SIGNAL_KEYGUARD_OFFSET_X, SettingsStore.DEFAULT_IOS_SIGNAL_KEYGUARD_OFFSET_X);
        addDivider(root);
        addOffsetSliderWithFallback(root, "\u9501\u5c4f\u4e0a\u4e0b\u504f\u79fb",
                "\u9501\u5c4f\u53f3\u4e0a\u89d2\u4fe1\u53f7\u683c\u7684\u9ad8\u5ea6\u8c03\u6574\u3002\u6b63\u6570\u5411\u4e0b\uff0c\u8d1f\u6570\u5411\u4e0a",
                enabledProfile ? SettingsStore.KEY_IOS_SIGNAL_KEYGUARD_OFFSET_Y : SettingsStore.KEY_IOS_SIGNAL_KEYGUARD_OFFSET_Y_OFF,
                enabledProfile ? SettingsStore.DEFAULT_IOS_SIGNAL_KEYGUARD_OFFSET_Y : SettingsStore.DEFAULT_IOS_SIGNAL_KEYGUARD_OFFSET_Y_OFF,
                SettingsStore.KEY_IOS_SIGNAL_KEYGUARD_OFFSET_Y, SettingsStore.DEFAULT_IOS_SIGNAL_KEYGUARD_OFFSET_Y);
        addDivider(root);
        addOffsetSliderWithFallback(root, "\u63a7\u5236\u4e2d\u5fc3\u5de6\u53f3\u504f\u79fb",
                "\u63a7\u5236\u4e2d\u5fc3\u548c\u8fd0\u8425\u5546\u533a\u5171\u7528\u8fd9\u7ec4\u4fe1\u53f7\u683c\u53c2\u6570\u3002\u6b63\u6570\u5411\u53f3\uff0c\u8d1f\u6570\u5411\u5de6",
                enabledProfile ? SettingsStore.KEY_IOS_SIGNAL_CONTROL_CENTER_OFFSET_X : SettingsStore.KEY_IOS_SIGNAL_CONTROL_CENTER_OFFSET_X_OFF,
                enabledProfile ? SettingsStore.DEFAULT_IOS_SIGNAL_CONTROL_CENTER_OFFSET_X : SettingsStore.DEFAULT_IOS_SIGNAL_CONTROL_CENTER_OFFSET_X_OFF,
                SettingsStore.KEY_IOS_SIGNAL_CONTROL_CENTER_OFFSET_X, SettingsStore.DEFAULT_IOS_SIGNAL_CONTROL_CENTER_OFFSET_X);
        addDivider(root);
        addOffsetSliderWithFallback(root, "\u63a7\u5236\u4e2d\u5fc3\u4e0a\u4e0b\u504f\u79fb",
                "\u63a7\u5236\u4e2d\u5fc3\u548c\u8fd0\u8425\u5546\u533a\u5171\u7528\u8fd9\u7ec4\u4fe1\u53f7\u683c\u53c2\u6570\u3002\u6b63\u6570\u5411\u4e0b\uff0c\u8d1f\u6570\u5411\u4e0a",
                enabledProfile ? SettingsStore.KEY_IOS_SIGNAL_CONTROL_CENTER_OFFSET_Y : SettingsStore.KEY_IOS_SIGNAL_CONTROL_CENTER_OFFSET_Y_OFF,
                enabledProfile ? SettingsStore.DEFAULT_IOS_SIGNAL_CONTROL_CENTER_OFFSET_Y : SettingsStore.DEFAULT_IOS_SIGNAL_CONTROL_CENTER_OFFSET_Y_OFF,
                SettingsStore.KEY_IOS_SIGNAL_CONTROL_CENTER_OFFSET_Y, SettingsStore.DEFAULT_IOS_SIGNAL_CONTROL_CENTER_OFFSET_Y);
    }

    private void addNetworkTypeProfileRows(LinearLayout root, boolean enabledProfile) {
        String factorKey = enabledProfile
                ? SettingsStore.KEY_NETWORK_TYPE_FACTOR
                : SettingsStore.KEY_NETWORK_TYPE_FACTOR_OFF;
        int factorDefault = enabledProfile
                ? SettingsStore.DEFAULT_NETWORK_TYPE_FACTOR
                : SettingsStore.DEFAULT_NETWORK_TYPE_FACTOR_OFF;
        addSliderRowWithFallback(root, "5G \u6807\u8bc6\u7f29\u653e\u5f3a\u5ea6",
                "mobile_type \u548c VoLTE \u7c7b\u56fe\u6807\u7684\u4e13\u5c5e\u7f29\u653e",
                factorKey, factorDefault,
                SettingsStore.KEY_NETWORK_TYPE_FACTOR, SettingsStore.DEFAULT_NETWORK_TYPE_FACTOR, 0, 160, "%");
        addDivider(root);
        addOffsetSliderWithFallback(root, "\u684c\u9762\u5de6\u53f3\u504f\u79fb",
                "\u684c\u9762\u72b6\u6001\u680f\u4e0b 5G \u6807\u8bc6\u7684\u4f4d\u7f6e\u8c03\u6574\u3002\u6b63\u6570\u5411\u53f3\uff0c\u8d1f\u6570\u5411\u5de6",
                enabledProfile ? SettingsStore.KEY_NETWORK_TYPE_DESKTOP_OFFSET_X : SettingsStore.KEY_NETWORK_TYPE_DESKTOP_OFFSET_X_OFF,
                enabledProfile ? SettingsStore.DEFAULT_NETWORK_TYPE_DESKTOP_OFFSET_X : SettingsStore.DEFAULT_NETWORK_TYPE_DESKTOP_OFFSET_X_OFF,
                SettingsStore.KEY_NETWORK_TYPE_DESKTOP_OFFSET_X, SettingsStore.DEFAULT_NETWORK_TYPE_DESKTOP_OFFSET_X);
        addDivider(root);
        addOffsetSliderWithFallback(root, "\u684c\u9762\u4e0a\u4e0b\u504f\u79fb",
                "\u684c\u9762\u72b6\u6001\u680f\u4e0b 5G \u6807\u8bc6\u7684\u9ad8\u5ea6\u8c03\u6574\u3002\u6b63\u6570\u5411\u4e0b\uff0c\u8d1f\u6570\u5411\u4e0a",
                enabledProfile ? SettingsStore.KEY_NETWORK_TYPE_DESKTOP_OFFSET_Y : SettingsStore.KEY_NETWORK_TYPE_DESKTOP_OFFSET_Y_OFF,
                enabledProfile ? SettingsStore.DEFAULT_NETWORK_TYPE_DESKTOP_OFFSET_Y : SettingsStore.DEFAULT_NETWORK_TYPE_DESKTOP_OFFSET_Y_OFF,
                SettingsStore.KEY_NETWORK_TYPE_DESKTOP_OFFSET_Y, SettingsStore.DEFAULT_NETWORK_TYPE_DESKTOP_OFFSET_Y);
        addDivider(root);
        addOffsetSliderWithFallback(root, "\u9501\u5c4f\u5de6\u53f3\u504f\u79fb",
                "\u9501\u5c4f\u72b6\u6001\u680f\u4e0b 5G \u6807\u8bc6\u7684\u4f4d\u7f6e\u8c03\u6574\u3002\u6b63\u6570\u5411\u53f3\uff0c\u8d1f\u6570\u5411\u5de6",
                enabledProfile ? SettingsStore.KEY_NETWORK_TYPE_KEYGUARD_OFFSET_X : SettingsStore.KEY_NETWORK_TYPE_KEYGUARD_OFFSET_X_OFF,
                enabledProfile ? SettingsStore.DEFAULT_NETWORK_TYPE_KEYGUARD_OFFSET_X : SettingsStore.DEFAULT_NETWORK_TYPE_KEYGUARD_OFFSET_X_OFF,
                SettingsStore.KEY_NETWORK_TYPE_KEYGUARD_OFFSET_X, SettingsStore.DEFAULT_NETWORK_TYPE_KEYGUARD_OFFSET_X);
        addDivider(root);
        addOffsetSliderWithFallback(root, "\u9501\u5c4f\u4e0a\u4e0b\u504f\u79fb",
                "\u9501\u5c4f\u72b6\u6001\u680f\u4e0b 5G \u6807\u8bc6\u7684\u9ad8\u5ea6\u8c03\u6574\u3002\u6b63\u6570\u5411\u4e0b\uff0c\u8d1f\u6570\u5411\u4e0a",
                enabledProfile ? SettingsStore.KEY_NETWORK_TYPE_KEYGUARD_OFFSET_Y : SettingsStore.KEY_NETWORK_TYPE_KEYGUARD_OFFSET_Y_OFF,
                enabledProfile ? SettingsStore.DEFAULT_NETWORK_TYPE_KEYGUARD_OFFSET_Y : SettingsStore.DEFAULT_NETWORK_TYPE_KEYGUARD_OFFSET_Y_OFF,
                SettingsStore.KEY_NETWORK_TYPE_KEYGUARD_OFFSET_Y, SettingsStore.DEFAULT_NETWORK_TYPE_KEYGUARD_OFFSET_Y);
        addDivider(root);
        addOffsetSliderWithFallback(root, "\u63a7\u5236\u4e2d\u5fc3\u5de6\u53f3\u504f\u79fb",
                "\u63a7\u5236\u4e2d\u5fc3\u548c\u8fd0\u8425\u5546\u533a\u5171\u7528\u8fd9\u7ec4 5G \u53c2\u6570\u3002\u6b63\u6570\u5411\u53f3\uff0c\u8d1f\u6570\u5411\u5de6",
                enabledProfile ? SettingsStore.KEY_NETWORK_TYPE_CONTROL_CENTER_OFFSET_X : SettingsStore.KEY_NETWORK_TYPE_CONTROL_CENTER_OFFSET_X_OFF,
                enabledProfile ? SettingsStore.DEFAULT_NETWORK_TYPE_CONTROL_CENTER_OFFSET_X : SettingsStore.DEFAULT_NETWORK_TYPE_CONTROL_CENTER_OFFSET_X_OFF,
                SettingsStore.KEY_NETWORK_TYPE_CONTROL_CENTER_OFFSET_X, SettingsStore.DEFAULT_NETWORK_TYPE_CONTROL_CENTER_OFFSET_X);
        addDivider(root);
        addOffsetSliderWithFallback(root, "\u63a7\u5236\u4e2d\u5fc3\u4e0a\u4e0b\u504f\u79fb",
                "\u63a7\u5236\u4e2d\u5fc3\u548c\u8fd0\u8425\u5546\u533a\u5171\u7528\u8fd9\u7ec4 5G \u53c2\u6570\u3002\u6b63\u6570\u5411\u4e0b\uff0c\u8d1f\u6570\u5411\u4e0a",
                enabledProfile ? SettingsStore.KEY_NETWORK_TYPE_CONTROL_CENTER_OFFSET_Y : SettingsStore.KEY_NETWORK_TYPE_CONTROL_CENTER_OFFSET_Y_OFF,
                enabledProfile ? SettingsStore.DEFAULT_NETWORK_TYPE_CONTROL_CENTER_OFFSET_Y : SettingsStore.DEFAULT_NETWORK_TYPE_CONTROL_CENTER_OFFSET_Y_OFF,
                SettingsStore.KEY_NETWORK_TYPE_CONTROL_CENTER_OFFSET_Y, SettingsStore.DEFAULT_NETWORK_TYPE_CONTROL_CENTER_OFFSET_Y);
    }

    private void showTopMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, MENU_IMPORT, 0, "\u5bfc\u5165\u914d\u7f6e");
        popup.getMenu().add(0, MENU_EXPORT, 1, "\u5bfc\u51fa\u914d\u7f6e");
        popup.getMenu().add(0, MENU_RESET, 2, "\u6062\u590d\u9ed8\u8ba4");
        popup.getMenu().add(0, MENU_RESTART, 3, "\u91cd\u542f SystemUI");
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == MENU_IMPORT) {
                startImportConfig();
                return true;
            }
            if (id == MENU_EXPORT) {
                startExportConfig();
                return true;
            }
            if (id == MENU_RESET) {
                resetAllSettings();
                return true;
            }
            if (id == MENU_RESTART) {
                restartSystemUi();
                return true;
            }
            return false;
        });
        popup.show();
    }

    private int readIntSetting(String key, int defaultValue) {
        return prefs.getInt(key, defaultValue);
    }

    private boolean readFeatureToggle(FeatureToggle toggleConfig) {
        boolean first = prefs.getBoolean(toggleConfig.primaryKey, toggleConfig.primaryDefaultValue);
        if (toggleConfig.secondaryKey == null) {
            return first;
        }
        boolean second = prefs.getBoolean(toggleConfig.secondaryKey, toggleConfig.secondaryDefaultValue);
        return first && second;
    }

    private int getIntValueWithFallback(String key, int defaultValue, String fallbackKey, int fallbackDefaultValue) {
        if (prefs.contains(key)) {
            return prefs.getInt(key, defaultValue);
        }
        return prefs.getInt(fallbackKey, fallbackDefaultValue);
    }

    private void putBooleanSetting(String key, boolean value) {
        prefs.edit().putBoolean(key, value).apply();
        SettingsStore.notifyChanged(this);
    }

    private void putFeatureToggle(FeatureToggle toggleConfig, boolean value) {
        SharedPreferences.Editor editor = prefs.edit().putBoolean(toggleConfig.primaryKey, value);
        if (toggleConfig.secondaryKey != null) {
            editor.putBoolean(toggleConfig.secondaryKey, value);
        }
        editor.apply();
        SettingsStore.notifyChanged(this);
    }

    private void putIntSetting(String key, int value) {
        prefs.edit().putInt(key, value).apply();
        SettingsStore.notifyChanged(this);
    }

    private void resetAllSettings() {
        prefs.edit().clear().apply();
        SettingsStore.notifyChanged(this);
        showToast("\u5df2\u6062\u590d\u9ed8\u8ba4\u914d\u7f6e");
        recreate();
    }

    private void resetSignalAndNetworkOffsets() {
        prefs.edit()
                .putInt(SettingsStore.KEY_IOS_SIGNAL_OFFSET_X, 0)
                .putInt(SettingsStore.KEY_IOS_SIGNAL_OFFSET_Y, 0)
                .putInt(SettingsStore.KEY_IOS_SIGNAL_DESKTOP_OFFSET_X, 0)
                .putInt(SettingsStore.KEY_IOS_SIGNAL_DESKTOP_OFFSET_Y, 0)
                .putInt(SettingsStore.KEY_IOS_SIGNAL_DESKTOP_OFFSET_X_OFF, 0)
                .putInt(SettingsStore.KEY_IOS_SIGNAL_DESKTOP_OFFSET_Y_OFF, 0)
                .putInt(SettingsStore.KEY_IOS_SIGNAL_KEYGUARD_OFFSET_X, 0)
                .putInt(SettingsStore.KEY_IOS_SIGNAL_KEYGUARD_OFFSET_Y, 0)
                .putInt(SettingsStore.KEY_IOS_SIGNAL_KEYGUARD_OFFSET_X_OFF, 0)
                .putInt(SettingsStore.KEY_IOS_SIGNAL_KEYGUARD_OFFSET_Y_OFF, 0)
                .putInt(SettingsStore.KEY_IOS_SIGNAL_CONTROL_CENTER_OFFSET_X, 0)
                .putInt(SettingsStore.KEY_IOS_SIGNAL_CONTROL_CENTER_OFFSET_Y, 0)
                .putInt(SettingsStore.KEY_IOS_SIGNAL_CONTROL_CENTER_OFFSET_X_OFF, 0)
                .putInt(SettingsStore.KEY_IOS_SIGNAL_CONTROL_CENTER_OFFSET_Y_OFF, 0)
                .putInt(SettingsStore.KEY_NETWORK_TYPE_OFFSET_X, 0)
                .putInt(SettingsStore.KEY_NETWORK_TYPE_OFFSET_Y, 0)
                .putInt(SettingsStore.KEY_NETWORK_TYPE_DESKTOP_OFFSET_X, 0)
                .putInt(SettingsStore.KEY_NETWORK_TYPE_DESKTOP_OFFSET_Y, 0)
                .putInt(SettingsStore.KEY_NETWORK_TYPE_DESKTOP_OFFSET_X_OFF, 0)
                .putInt(SettingsStore.KEY_NETWORK_TYPE_DESKTOP_OFFSET_Y_OFF, 0)
                .putInt(SettingsStore.KEY_NETWORK_TYPE_KEYGUARD_OFFSET_X, 0)
                .putInt(SettingsStore.KEY_NETWORK_TYPE_KEYGUARD_OFFSET_Y, 0)
                .putInt(SettingsStore.KEY_NETWORK_TYPE_KEYGUARD_OFFSET_X_OFF, 0)
                .putInt(SettingsStore.KEY_NETWORK_TYPE_KEYGUARD_OFFSET_Y_OFF, 0)
                .putInt(SettingsStore.KEY_NETWORK_TYPE_CONTROL_CENTER_OFFSET_X, 0)
                .putInt(SettingsStore.KEY_NETWORK_TYPE_CONTROL_CENTER_OFFSET_Y, 0)
                .putInt(SettingsStore.KEY_NETWORK_TYPE_CONTROL_CENTER_OFFSET_X_OFF, 0)
                .putInt(SettingsStore.KEY_NETWORK_TYPE_CONTROL_CENTER_OFFSET_Y_OFF, 0)
                .apply();
        SettingsStore.notifyChanged(this);
        showToast("\u4fe1\u53f7\u683c\u548c 5G \u6807\u8bc6\u7684\u504f\u79fb\u5df2\u6062\u590d\u4e3a 0");
        recreate();
    }

    private String formatValue(int value, String suffix) {
        return suffix == null || suffix.length() == 0 ? Integer.toString(value) : value + suffix;
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
            applyOffProfileFallback(editor, settings,
                    SettingsStore.KEY_MOBILE_SIGNAL_FACTOR_OFF, SettingsStore.KEY_MOBILE_SIGNAL_FACTOR);
            applyOffProfileFallback(editor, settings,
                    SettingsStore.KEY_IOS_SIGNAL_DESKTOP_OFFSET_X_OFF, SettingsStore.KEY_IOS_SIGNAL_DESKTOP_OFFSET_X);
            applyOffProfileFallback(editor, settings,
                    SettingsStore.KEY_IOS_SIGNAL_DESKTOP_OFFSET_Y_OFF, SettingsStore.KEY_IOS_SIGNAL_DESKTOP_OFFSET_Y);
            applyOffProfileFallback(editor, settings,
                    SettingsStore.KEY_IOS_SIGNAL_KEYGUARD_OFFSET_X_OFF, SettingsStore.KEY_IOS_SIGNAL_KEYGUARD_OFFSET_X);
            applyOffProfileFallback(editor, settings,
                    SettingsStore.KEY_IOS_SIGNAL_KEYGUARD_OFFSET_Y_OFF, SettingsStore.KEY_IOS_SIGNAL_KEYGUARD_OFFSET_Y);
            applyOffProfileFallback(editor, settings,
                    SettingsStore.KEY_IOS_SIGNAL_CONTROL_CENTER_OFFSET_X_OFF, SettingsStore.KEY_IOS_SIGNAL_CONTROL_CENTER_OFFSET_X);
            applyOffProfileFallback(editor, settings,
                    SettingsStore.KEY_IOS_SIGNAL_CONTROL_CENTER_OFFSET_Y_OFF, SettingsStore.KEY_IOS_SIGNAL_CONTROL_CENTER_OFFSET_Y);
            applyOffProfileFallback(editor, settings,
                    SettingsStore.KEY_NETWORK_TYPE_FACTOR_OFF, SettingsStore.KEY_NETWORK_TYPE_FACTOR);
            applyOffProfileFallback(editor, settings,
                    SettingsStore.KEY_NETWORK_TYPE_DESKTOP_OFFSET_X_OFF, SettingsStore.KEY_NETWORK_TYPE_DESKTOP_OFFSET_X);
            applyOffProfileFallback(editor, settings,
                    SettingsStore.KEY_NETWORK_TYPE_DESKTOP_OFFSET_Y_OFF, SettingsStore.KEY_NETWORK_TYPE_DESKTOP_OFFSET_Y);
            applyOffProfileFallback(editor, settings,
                    SettingsStore.KEY_NETWORK_TYPE_KEYGUARD_OFFSET_X_OFF, SettingsStore.KEY_NETWORK_TYPE_KEYGUARD_OFFSET_X);
            applyOffProfileFallback(editor, settings,
                    SettingsStore.KEY_NETWORK_TYPE_KEYGUARD_OFFSET_Y_OFF, SettingsStore.KEY_NETWORK_TYPE_KEYGUARD_OFFSET_Y);
            applyOffProfileFallback(editor, settings,
                    SettingsStore.KEY_NETWORK_TYPE_CONTROL_CENTER_OFFSET_X_OFF, SettingsStore.KEY_NETWORK_TYPE_CONTROL_CENTER_OFFSET_X);
            applyOffProfileFallback(editor, settings,
                    SettingsStore.KEY_NETWORK_TYPE_CONTROL_CENTER_OFFSET_Y_OFF, SettingsStore.KEY_NETWORK_TYPE_CONTROL_CENTER_OFFSET_Y);
            editor.apply();
            SettingsStore.notifyChanged(this);
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

    private void showIntInputDialog(String titleText, int currentValue, int min, int max, String suffix,
            IntValueConsumer consumer) {
        EditText input = new EditText(this);
        input.setText(String.valueOf(currentValue));
        input.setSelection(input.getText().length());
        input.setInputType(min < 0
                ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED
                : InputType.TYPE_CLASS_NUMBER);
        input.setHint(min + " ~ " + max);
        int padding = dp(20);
        input.setPadding(padding, padding, padding, padding);

        new AlertDialog.Builder(this)
                .setTitle(titleText)
                .setMessage("\u8f93\u5165\u8303\u56f4 " + min + " ~ " + max + (suffix == null ? "" : suffix))
                .setView(input)
                .setNegativeButton("\u53d6\u6d88", null)
                .setPositiveButton("\u786e\u5b9a", (dialog, which) -> {
                    String text = input.getText() == null ? "" : input.getText().toString().trim();
                    if (text.length() == 0) {
                        showToast("\u8bf7\u8f93\u5165\u6570\u503c");
                        return;
                    }
                    try {
                        int value = Integer.parseInt(text);
                        int clamped = Math.max(min, Math.min(max, value));
                        consumer.accept(clamped);
                    } catch (NumberFormatException ignored) {
                        showToast("\u8f93\u5165\u683c\u5f0f\u4e0d\u6b63\u786e");
                    }
                })
                .show();
    }

    private void applyOffProfileFallback(SharedPreferences.Editor editor, JSONObject settings,
            String offKey, String onKey) {
        if (!settings.has(offKey) && settings.has(onKey)) {
            editor.putInt(offKey, settings.optInt(onKey, SettingsStore.defaultInt(offKey)));
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void restartSystemUi() {
        showToast("\u6b63\u5728\u91cd\u542f SystemUI...");
        new Thread(() -> {
            boolean success = false;
            String error = null;
            try {
                Process process = new ProcessBuilder("su", "-c",
                        "pkill -f com.android.systemui || killall com.android.systemui")
                        .redirectErrorStream(true)
                        .start();
                success = process.waitFor() == 0;
            } catch (Throwable t) {
                error = t.getMessage();
            }
            boolean finalSuccess = success;
            String finalError = error;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (finalSuccess) {
                    showToast("SystemUI \u5df2\u91cd\u542f");
                } else if (finalError == null || finalError.length() == 0) {
                    showToast("\u91cd\u542f\u5931\u8d25\uff0c\u8bf7\u786e\u8ba4\u5df2\u6388\u4e88 root \u6743\u9650");
                } else {
                    showToast("\u91cd\u542f\u5931\u8d25\uff1a" + finalError);
                }
            });
        }).start();
    }

    private LinearLayout card(int color, int radiusDp) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(roundRect(color, radiusDp));
        return card;
    }

    private LinearLayout card(int color, int strokeColor, int radiusDp) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(outlinedRect(color, strokeColor, 1, radiusDp));
        return card;
    }

    private TextView chip(String text, int backgroundColor, int textColor) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(textColor);
        view.setTextSize(12);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(12), dp(6), dp(12), dp(6));
        view.setBackground(roundRect(backgroundColor, 99));
        return view;
    }

    private TextView filledButton(String text, int backgroundColor, int textColor) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(textColor);
        view.setTextSize(14);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(14), dp(10), dp(14), dp(10));
        view.setBackground(roundRect(backgroundColor, 999));
        return view;
    }

    private GradientDrawable gradientCard() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{colorPrimaryContainer, colorPrimary, colorPrimaryDeep});
        drawable.setCornerRadius(dp(32));
        return drawable;
    }

    private void initPalette() {
        colorBackground = resolveMonetColor("system_neutral1_10", FALLBACK_BACKGROUND);
        colorSurface = resolveMonetColor("system_neutral1_0", FALLBACK_SURFACE);
        colorSurfaceSoft = resolveMonetColor("system_neutral2_50", FALLBACK_SURFACE_SOFT);
        colorSurfaceStrong = resolveMonetColor("system_accent1_100", FALLBACK_SURFACE_STRONG);
        colorFeatureSurface = resolveMonetColor("system_accent2_50", FALLBACK_FEATURE_SURFACE);
        colorFeatureStroke = resolveMonetColor("system_accent1_200", FALLBACK_FEATURE_STROKE);
        colorText = resolveMonetColor("system_neutral1_900", FALLBACK_TEXT);
        colorSubtext = resolveMonetColor("system_neutral2_700", FALLBACK_SUBTEXT);
        colorPrimary = resolveMonetColor("system_accent1_600", FALLBACK_PRIMARY);
        colorPrimaryContainer = resolveMonetColor("system_accent1_300", FALLBACK_PRIMARY_CONTAINER);
        colorPrimaryDeep = resolveMonetColor("system_accent1_800", FALLBACK_PRIMARY_DEEP);
        colorStroke = resolveMonetColor("system_neutral2_200", FALLBACK_STROKE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(colorBackground);
            getWindow().setNavigationBarColor(colorSurface);
        }
    }

    private int resolveMonetColor(String androidColorName, int fallback) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return fallback;
        }
        try {
            Resources resources = Resources.getSystem();
            int resId = resources.getIdentifier(androidColorName, "color", "android");
            if (resId != 0) {
                return resources.getColor(resId, getTheme());
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private GradientDrawable outlinedRect(int color, int strokeColor, int strokeWidthDp, int radiusDp) {
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

    private FrameLayout.LayoutParams floatingMenuLayoutParams() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.topMargin = dp(16);
        lp.rightMargin = dp(20);
        return lp;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private interface IntValueConsumer {
        void accept(int value);
    }

    private static final class FeatureToggle {
        final String primaryKey;
        final boolean primaryDefaultValue;
        final String secondaryKey;
        final boolean secondaryDefaultValue;
        final boolean readOnly;

        private FeatureToggle(String primaryKey, boolean primaryDefaultValue,
                String secondaryKey, boolean secondaryDefaultValue, boolean readOnly) {
            this.primaryKey = primaryKey;
            this.primaryDefaultValue = primaryDefaultValue;
            this.secondaryKey = secondaryKey;
            this.secondaryDefaultValue = secondaryDefaultValue;
            this.readOnly = readOnly;
        }

        static FeatureToggle createLinked(String primaryKey, boolean primaryDefaultValue,
                String secondaryKey, boolean secondaryDefaultValue) {
            return new FeatureToggle(primaryKey, primaryDefaultValue, secondaryKey, secondaryDefaultValue, false);
        }

        static FeatureToggle single(String primaryKey, boolean primaryDefaultValue) {
            return new FeatureToggle(primaryKey, primaryDefaultValue, null, false, false);
        }

        static FeatureToggle readOnlyLinked(String primaryKey, boolean primaryDefaultValue,
                String secondaryKey, boolean secondaryDefaultValue) {
            return new FeatureToggle(primaryKey, primaryDefaultValue, secondaryKey, secondaryDefaultValue, true);
        }
    }
}
