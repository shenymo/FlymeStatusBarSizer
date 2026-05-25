package com.example.flymestatusbarsizer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int REQUEST_EXPORT_CONFIG = 1001;
    private static final int REQUEST_IMPORT_CONFIG = 1002;

    private static final int MENU_ABOUT = 1;
    private static final int MENU_RESTART = 5;
    static final String IME_CONTROL_BAR_DRAG_LABEL = "ime_control_bar_button";
    static final int IME_CONTROL_BAR_POOL_ROW_ITEM_COUNT = 3;
    private static final String PACKAGE_SYSTEM_UI = "com.android.systemui";
    private static final String PACKAGE_FLYME_LAUNCHER = "com.meizu.flyme.launcher";
    private static final long SYSTEM_UI_RESTART_DELAY_MS = 600L;
    private static final String GITHUB_URL = "https://github.com/shenymo/FlymeStatusBarSizer";
    private static final String QQ_GROUP_URL = "https://qun.qq.com/universal-share/share?ac=1&authKey=WuaHYIEHdI6Y%2Fvn7SvcFMtyuUX%2Bwp%2FMedY0eMgPLq9Bbrz%2FPMRsiIgDttNOMbPWW&busi_data=eyJncm91cENvZGUiOiIxMTAyMTM4MzgxIiwidG9rZW4iOiJIb1hmV2xvaVUxWFk2YjAyOXl5MmIwelljU3A5bFRYejQrb3JtUlJwOXRMK1BLU3pnWWRaSG9VdHZ4M3Fld2xqIiwidWluIjoiMjI4OTU3MTk5MCJ9&data=O3ClX619ry0x93elARpxRoHiwSavPU_N00zhT1jj5d_rR0feICi-g7gudqIpU6sbrKtr1_CCPBpNQ-APojGliw&svctype=4&tempid=h5_group_info";
    private static final String QQ_GROUP_NUMBER = "1102138381";
    static final Pattern CLOCK_EXPRESSION_TOKEN_PATTERN = Pattern.compile("\\{([A-Za-z0-9_]+)\\}");
    static final String[][] CLOCK_EXPRESSION_TOKEN_ROWS = {
            {"HH", "H", "hh", "h"},
            {"mm", "ss", "ampm", "period"},
            {"week", "week_short", "week_1"},
            {"branch", "branch_alias"}
    };
    private static final int POSITION_OFFSET_MIN_DP = -24;
    private static final int POSITION_OFFSET_MAX_DP = 24;
    private static final int POSITION_OFFSET_MIN_TENTH_DP = POSITION_OFFSET_MIN_DP * 10;
    private static final int POSITION_OFFSET_MAX_TENTH_DP = POSITION_OFFSET_MAX_DP * 10;
    private static final String[] POSITION_TUNING_KEYS = {
            SettingsStore.KEY_CLOCK_RIGHT_PADDING_OFFSET_DP,
            SettingsStore.KEY_BATTERY_ICON_Y_OFFSET_DP,
            SettingsStore.KEY_BATTERY_TEXT_Y_OFFSET_DP,
            SettingsStore.KEY_BATTERY_BOLT_Y_OFFSET_DP,
            SettingsStore.KEY_SIGNAL_SINGLE_Y_OFFSET_DP,
            SettingsStore.KEY_SIGNAL_BADGE_Y_OFFSET_DP,
            SettingsStore.KEY_SIGNAL_DUAL_Y_OFFSET_DP,
            SettingsStore.KEY_WIFI_Y_OFFSET_DP,
            SettingsStore.KEY_IME_CONTROL_BAR_Y_OFFSET_DP
    };

    private static final int FALLBACK_BACKGROUND = Color.rgb(248, 249, 251);
    private static final int FALLBACK_SURFACE = Color.WHITE;
    private static final int FALLBACK_SURFACE_SOFT = Color.rgb(242, 244, 246);
    private static final int FALLBACK_SURFACE_STRONG = Color.rgb(231, 232, 234);
    private static final int FALLBACK_FEATURE_SURFACE = Color.rgb(245, 249, 255);
    private static final int FALLBACK_FEATURE_STROKE = Color.rgb(192, 198, 214);
    private static final int FALLBACK_TEXT = Color.rgb(25, 28, 30);
    private static final int FALLBACK_SUBTEXT = Color.rgb(64, 71, 84);
    private static final int FALLBACK_PRIMARY = Color.rgb(0, 92, 174);
    private static final int FALLBACK_PRIMARY_CONTAINER = Color.rgb(0, 116, 217);
    private static final int FALLBACK_PRIMARY_DEEP = Color.rgb(0, 71, 136);
    private static final int FALLBACK_STROKE = Color.rgb(192, 198, 214);

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
    private final ArrayList<PositionOffsetSliderBinding> positionTuningSliderBindings = new ArrayList<>();
    private final HashMap<String, Integer> pendingIntSliderValues = new HashMap<>();
    private final HashMap<String, Integer> pendingPositionOffsetValues = new HashMap<>();
    private LinearLayout topBar;
    private TextView backButtonView;
    private TextView moreButtonView;
    private TextView topBarEyebrowView;
    private TextView topBarTitleView;
    private TextView topBarSubtitleView;
    private FrameLayout pageHostView;
    private OnBackInvokedCallback systemBackCallback;
    private boolean systemBackCallbackRegistered;
    private final Map<Page, View> pageViews = new LinkedHashMap<>();
    private final ArrayDeque<Page> navigationStack = new ArrayDeque<>();
    private Page currentPage = Page.HOME;
    private final ClockExpressionEditor clockExpressionEditor = new ClockExpressionEditor(this);
    private final ClockDetailActionGridEditor clockDetailActionGridEditor =
            new ClockDetailActionGridEditor(this);
    private final ImeToolbarEditor imeToolbarEditor = new ImeToolbarEditor(this);
    private final SettingsCardFactory settingsCardFactory = new SettingsCardFactory(this);
    private final SettingsUiFactory settingsUiFactory = new SettingsUiFactory(this);

    enum Page {
        HOME(null, null, null, false),
        ICONS_BATTERY("图标与电池", "状态栏图标缩放、电池样式、通知图标以及信号与 Wi-Fi 接管设置。", null, true),
        TIME_NETWORK("时间与网络", "实时网速显隐阈值、时间表达式、时钟详情弹窗，以及时间字重字号设置。", null, true),
        SYSTEM_INTERACTION("系统交互", "MBack 长触、导航栏沉浸与高度、输入法控制栏接管，以及系统桌面后台卡片布局调整。", null, true),
        SYSTEM_APPEARANCE("系统外观", "云端图标下发和 Flyme 桌面外观相关控制。", null, true),
        ADVANCED_DEBUG("高级与调试", "配置管理、WIFI 性能打点，以及高阶工具入口。", null, true),
        ABOUT("关于与支持", "项目地址、交流群、版本构建信息和目标作用域说明。", null, false),
        DONATION("捐赠", null, null, false),
        POSITION_TUNING("布局微调", "单独调整时钟、电池、信号、Wi-Fi 与输入法控制栏的细节位置。", null, true),
        TELEPHONY_DEBUG("Telephony 调试", "伪造 Telephony 读数，验证双卡、网络制式与信号等级对图标布局的影响。", null, true);

        final String title;
        final String subtitle;
        final String eyebrow;
        final boolean showMore;

        Page(String title, String subtitle, String eyebrow, boolean showMore) {
            this.title = title;
            this.subtitle = subtitle;
            this.eyebrow = eyebrow;
            this.showMore = showMore;
        }
    }

    interface PageBinder {
        void bind(MainActivity activity, LinearLayout root);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = SettingsStore.prefs(this);
        SettingsStore.prepareRemoteSync(this);
        initPalette();
        configureSystemBars();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(true);
        }
        setContentView(R.layout.activity_main);
        bindHostViews();
        bindPages();
        showPage(Page.HOME);
    }

    @Override
    public void onBackPressed() {
        if (handleBackNavigation()) {
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        unregisterSystemBackCallback();
        super.onDestroy();
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

    private void bindHostViews() {
        topBar = findViewById(R.id.top_bar);
        backButtonView = findViewById(R.id.back_button);
        moreButtonView = findViewById(R.id.more_button);
        topBarEyebrowView = findViewById(R.id.top_bar_eyebrow);
        topBarTitleView = findViewById(R.id.top_bar_title);
        topBarSubtitleView = findViewById(R.id.top_bar_subtitle);
        pageHostView = findViewById(R.id.page_host);
        if (topBar != null) {
            topBar.setBackgroundColor(colorBackground);
            int topInset = getStatusBarInset();
            topBar.setPadding(
                    topBar.getPaddingLeft(),
                    dp(12) + topInset,
                    topBar.getPaddingRight(),
                    topBar.getPaddingBottom());
        }
        if (pageHostView != null) {
            pageHostView.setBackgroundColor(colorBackground);
        }
        setTapClickListener(backButtonView, v -> onBackPressed());
        setTapClickListener(moreButtonView, this::showMoreMenu);
    }

    private void bindPages() {
        pageViews.clear();
        registerPage(Page.HOME, R.layout.page_home, HomePageController::bind);
        registerPage(Page.ICONS_BATTERY, R.layout.page_icons_battery, IconsBatteryPageController::bind);
        registerPage(Page.TIME_NETWORK, R.layout.page_time_network, TimeNetworkPageController::bind);
        registerPage(Page.SYSTEM_INTERACTION, R.layout.page_system_interaction,
                SystemInteractionPageController::bind);
        registerPage(Page.SYSTEM_APPEARANCE, R.layout.page_system_interaction,
                (activity, root) -> root.addView(activity.createSystemAppearanceSettingsCard(),
                        PageViewUtils.matchWrap()));
        registerPage(Page.ADVANCED_DEBUG, R.layout.page_advanced_debug, AdvancedDebugPageController::bind);
        registerPage(Page.ABOUT, R.layout.page_about, AboutPageController::bind);
        registerPage(Page.DONATION, R.layout.page_donation, null);
        registerPage(Page.POSITION_TUNING, R.layout.page_position_tuning, PositionTuningPageController::bind);
        registerPage(Page.TELEPHONY_DEBUG, R.layout.page_telephony_debug, TelephonyDebugPageController::bind);
    }

    private void registerPage(Page page, int layoutResId, PageBinder binder) {
        if (pageHostView == null) {
            return;
        }
        View pageView = getLayoutInflater().inflate(layoutResId, pageHostView, false);
        LinearLayout container = pageView.findViewById(R.id.page_content);
        if (binder != null && container != null) {
            binder.bind(this, container);
        }
        pageView.setVisibility(View.GONE);
        pageHostView.addView(pageView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        pageViews.put(page, pageView);
    }

    void openPage(Page page) {
        if (page == null || page == currentPage) {
            return;
        }
        navigationStack.push(currentPage);
        showPage(page);
    }

    void showPage(Page page) {
        if (page == null) {
            return;
        }
        currentPage = page;
        for (Map.Entry<Page, View> entry : pageViews.entrySet()) {
            entry.getValue().setVisibility(entry.getKey() == page ? View.VISIBLE : View.GONE);
        }
        resetPageTransforms();
        updateTopBar(page);
        updateSystemBackCallbackRegistration();
    }

    private void updateTopBar(Page page) {
        if (page == null) {
            return;
        }
        boolean onHome = page == Page.HOME && navigationStack.isEmpty();
        if (topBar != null) {
            topBar.setVisibility(onHome ? View.GONE : View.VISIBLE);
        }
        if (backButtonView != null) {
            backButtonView.setVisibility(onHome ? View.GONE : View.VISIBLE);
        }
        if (moreButtonView != null) {
            moreButtonView.setVisibility(!onHome && page.showMore ? View.VISIBLE : View.GONE);
        }
        if (topBarEyebrowView != null) {
            if (TextUtils.isEmpty(page.eyebrow)) {
                topBarEyebrowView.setVisibility(View.GONE);
            } else {
                topBarEyebrowView.setText(page.eyebrow);
                topBarEyebrowView.setVisibility(View.VISIBLE);
            }
        }
        if (topBarTitleView != null) {
            topBarTitleView.setText(page.title);
            topBarTitleView.setTextSize(onHome ? 28f : 22f);
        }
        if (topBarSubtitleView != null) {
            topBarSubtitleView.setText(page.subtitle);
            topBarSubtitleView.setVisibility(TextUtils.isEmpty(page.subtitle) ? View.GONE : View.VISIBLE);
        }
    }

    private void showMoreMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, MENU_ABOUT, 0, "关于与支持");
        popup.getMenu().add(0, MENU_RESTART, 1, "重启 SystemUI");
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == MENU_ABOUT) {
                performTapHaptic(anchor);
                openPage(Page.ABOUT);
                return true;
            }
            if (id == MENU_RESTART) {
                performTapHaptic(anchor);
                restartSystemUi();
                return true;
            }
            return false;
        });
        popup.show();
    }

    private boolean handleBackNavigation() {
        if (currentPage != Page.HOME || !navigationStack.isEmpty()) {
            navigationStack.clear();
            showPage(Page.HOME);
            return true;
        }
        return false;
    }

    private void updateSystemBackCallbackRegistration() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        boolean shouldIntercept = currentPage != Page.HOME;
        if (shouldIntercept) {
            registerSystemBackCallback();
        } else {
            unregisterSystemBackCallback();
        }
    }

    private void registerSystemBackCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || systemBackCallbackRegistered) {
            return;
        }
        if (systemBackCallback == null) {
            systemBackCallback = createSystemBackCallback();
        }
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                systemBackCallback);
        systemBackCallbackRegistered = true;
    }

    private void unregisterSystemBackCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || !systemBackCallbackRegistered
                || systemBackCallback == null) {
            return;
        }
        getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(systemBackCallback);
        systemBackCallbackRegistered = false;
    }

    private void handleSystemBackInvoked() {
        if (!handleBackNavigation()) {
            finish();
        }
    }

    private OnBackInvokedCallback createSystemBackCallback() {
        return this::handleSystemBackInvoked;
    }

    private void resetPageTransforms() {
        for (View pageView : pageViews.values()) {
            pageView.setTranslationX(0f);
            pageView.setTranslationY(0f);
            pageView.setScaleX(1f);
            pageView.setScaleY(1f);
            pageView.setAlpha(1f);
        }
    }

    void showTelephonyDebugPage() {
        openPage(Page.TELEPHONY_DEBUG);
    }

    void showPositionTuningPage() {
        openPage(Page.POSITION_TUNING);
    }

    void showClockDetailActionGridEditor() {
        clockDetailActionGridEditor.show();
    }

    Switch addSwitchRow(LinearLayout root, String titleText, String subtitleText,
            String key, boolean defaultValue) {
        return addSwitchRow(root, titleText, subtitleText, key, defaultValue, null);
    }

    Switch addSwitchRow(LinearLayout root, String titleText, String subtitleText,
            String key, boolean defaultValue, CompoundButton.OnCheckedChangeListener extraListener) {
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
        styleSwitch(toggle);
        toggle.setChecked(SettingsStore.readBoolean(prefs, key, defaultValue));
        toggle.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
            if (buttonView.isPressed()) {
                performTapHaptic(buttonView);
            }
            putBooleanSetting(key, isChecked);
            if (extraListener != null) {
                extraListener.onCheckedChanged(buttonView, isChecked);
            }
        });

        row.addView(textColumn, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(toggle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(row, matchWrap());
        return toggle;
    }

    LinearLayout buildBatteryHollowOptions() {
        LinearLayout card = card(colorSurfaceSoft, colorStroke, 22);
        TextView title = new TextView(this);
        title.setText("镂空电池");
        title.setTextColor(colorPrimary);
        title.setTextSize(13);
        card.addView(title, matchWrap());
        addDivider(card);
        addSwitchRow(card, "电池内填充色随容量变化",
                "关闭时内部始终填满；开启后内部填充会按剩余电量缩短，未填充部分保留灰色底色。",
                SettingsStore.KEY_BATTERY_HOLLOW_FILL_FOLLOWS_LEVEL,
                SettingsStore.DEFAULT_BATTERY_HOLLOW_FILL_FOLLOWS_LEVEL);
        return card;
    }

    void addSliderRow(LinearLayout root, String titleText, String subtitleText, String key,
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
        styleSeekBar(seekBar);
        seekBar.setMax(max - min);
        seekBar.setProgress(clamped - min);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = min + progress;
                valueView.setText(formatValue(value, suffix));
                if (fromUser) {
                    performSliderHaptic(seekBar);
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
        setTapClickListener(valueView, v -> showIntInputDialog(
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

    void addPositionOffsetSliderRow(LinearLayout root, String titleText, String subtitleText,
            String key, int defaultValueTenthDp) {
        addPositionOffsetSliderRow(
                root,
                titleText,
                subtitleText,
                key,
                defaultValueTenthDp,
                getPositionOffsetMinTenthDp(key),
                getPositionOffsetMaxTenthDp(key));
    }

    void addPositionOffsetSliderRow(LinearLayout root, String titleText, String subtitleText,
            String key, int defaultValueTenthDp, int minTenthDp, int maxTenthDp) {
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
        styleSeekBar(seekBar);
        seekBar.setMax((maxTenthDp - minTenthDp) / 10);
        PositionOffsetSliderBinding binding =
                new PositionOffsetSliderBinding(valueView, seekBar, minTenthDp, maxTenthDp);
        int current = getPendingPositionOffsetValue(key, defaultValueTenthDp, minTenthDp, maxTenthDp);
        binding.setValue(current);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = sliderProgressToPositionOffsetTenthDp(progress, minTenthDp, maxTenthDp);
                valueView.setText(formatOffsetValue(value));
                if (fromUser) {
                    performSliderHaptic(seekBar);
                    updatePendingPositionOffsetValue(key, value, minTenthDp, maxTenthDp);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                updatePendingPositionOffsetValue(
                        key,
                        sliderProgressToPositionOffsetTenthDp(
                                seekBar.getProgress(),
                                minTenthDp,
                                maxTenthDp),
                        minTenthDp,
                        maxTenthDp);
            }
        });
        setTapClickListener(valueView, v -> showDecimalInputDialog(
                titleText,
                getPendingPositionOffsetValue(key, defaultValueTenthDp, minTenthDp, maxTenthDp),
                minTenthDp,
                maxTenthDp,
                value -> {
                    binding.setValue(value);
                    updatePendingPositionOffsetValue(key, value, minTenthDp, maxTenthDp);
                }));

        positionTuningSliderBindings.add(binding);
        row.addView(header, matchWrap());
        row.addView(subtitle, matchWrap());
        row.addView(seekBar, matchWrapWithTop(8));
        root.addView(row, matchWrap());
    }

    void addApplyPositionOffsetSliderRow(LinearLayout root, String titleText,
            String subtitleText, String key, int defaultValueTenthDp) {
        addApplyPositionOffsetSliderRow(
                root,
                titleText,
                subtitleText,
                key,
                defaultValueTenthDp,
                getPositionOffsetMinTenthDp(key),
                getPositionOffsetMaxTenthDp(key));
    }

    void addApplyPositionOffsetSliderRow(LinearLayout root, String titleText,
            String subtitleText, String key, int defaultValueTenthDp,
            int minTenthDp, int maxTenthDp) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(colorText);
        title.setTextSize(16);
        header.addView(title, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f));

        TextView valueView = new TextView(this);
        valueView.setTextColor(colorPrimary);
        valueView.setTextSize(14);
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
        styleSeekBar(seekBar);
        seekBar.setMax((maxTenthDp - minTenthDp) / 10);
        PositionOffsetSliderBinding binding =
                new PositionOffsetSliderBinding(valueView, seekBar, minTenthDp, maxTenthDp);
        int current = getPendingPositionOffsetValue(key, defaultValueTenthDp, minTenthDp, maxTenthDp);
        binding.setValue(current);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = sliderProgressToPositionOffsetTenthDp(progress, minTenthDp, maxTenthDp);
                valueView.setText(formatOffsetValue(value));
                if (fromUser) {
                    performSliderHaptic(seekBar);
                    updatePendingPositionOffsetValue(key, value, minTenthDp, maxTenthDp);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                updatePendingPositionOffsetValue(
                        key,
                        sliderProgressToPositionOffsetTenthDp(
                                seekBar.getProgress(),
                                minTenthDp,
                                maxTenthDp),
                        minTenthDp,
                        maxTenthDp);
            }
        });
        setTapClickListener(valueView, v -> showDecimalInputDialog(
                titleText,
                getPendingPositionOffsetValue(key, defaultValueTenthDp, minTenthDp, maxTenthDp),
                minTenthDp,
                maxTenthDp,
                value -> {
                    binding.setValue(value);
                    updatePendingPositionOffsetValue(key, value, minTenthDp, maxTenthDp);
                }));

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.END);

        TextView applyButton = filledButton("应用", colorPrimary, Color.WHITE);
        setTapClickListener(applyButton,
                v -> applyPendingPositionOffsetValue(
                        key,
                        defaultValueTenthDp,
                        minTenthDp,
                        maxTenthDp,
                        titleText));
        actionRow.addView(applyButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        row.addView(header, matchWrap());
        row.addView(subtitle, matchWrap());
        row.addView(seekBar, matchWrapWithTop(8));
        row.addView(actionRow, matchWrapWithTop(10));
        root.addView(row, matchWrap());
    }

    void addApplySliderRow(LinearLayout root, String titleText, String subtitleText, String key,
            int defaultValue, int min, int max, String suffix) {
        addApplySliderRowInternal(root, titleText, subtitleText, key, defaultValue, min, max, suffix, false);
    }

    void addApplyInsetSliderRow(LinearLayout root, String titleText, String subtitleText,
            String key, int defaultValue, int min, int max) {
        addApplySliderRowInternal(root, titleText, subtitleText, key, defaultValue, min, max, "", true);
    }

    void addApplySliderRowInternal(LinearLayout root, String titleText, String subtitleText, String key,
            int defaultValue, int min, int max, String suffix, boolean insetValue) {
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
        valueView.setPadding(dp(12), dp(8), dp(12), dp(8));
        valueView.setBackground(roundRect(colorSurfaceSoft, 999));
        int clamped = getPendingIntSliderValue(key, defaultValue, min, max);
        valueView.setText(formatSliderDisplayValue(clamped, suffix, insetValue));
        header.addView(valueView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView subtitle = new TextView(this);
        subtitle.setText(subtitleText);
        subtitle.setTextColor(colorSubtext);
        subtitle.setTextSize(13);
        subtitle.setPadding(0, dp(4), 0, 0);

        SeekBar seekBar = new SeekBar(this);
        styleSeekBar(seekBar);
        seekBar.setMax(max - min);
        seekBar.setProgress(clamped - min);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = min + progress;
                valueView.setText(formatSliderDisplayValue(value, suffix, insetValue));
                if (fromUser) {
                    performSliderHaptic(seekBar);
                    updatePendingIntSliderValue(key, value, min, max);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                updatePendingIntSliderValue(key, min + seekBar.getProgress(), min, max);
            }
        });
        setTapClickListener(valueView, v -> showIntInputDialog(
                titleText,
                min + seekBar.getProgress(),
                min,
                max,
                insetValue ? "" : suffix,
                value -> {
                    updatePendingIntSliderValue(key, value, min, max);
                    valueView.setText(formatSliderDisplayValue(value, suffix, insetValue));
                    seekBar.setProgress(value - min);
                }));

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.END);

        TextView applyButton = filledButton("应用", colorPrimary, Color.WHITE);
        setTapClickListener(applyButton,
                v -> applyPendingIntSliderValue(key, defaultValue, min, max, titleText));
        actionRow.addView(applyButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        row.addView(header, matchWrap());
        row.addView(subtitle, matchWrap());
        row.addView(seekBar, matchWrapWithTop(8));
        row.addView(actionRow, matchWrapWithTop(10));
        root.addView(row, matchWrap());
    }

    void addSliderRowWithFallback(LinearLayout root, String titleText, String subtitleText, String key,
            int defaultValue, String fallbackKey, int fallbackDefaultValue, int min, int max, String suffix) {
        int initialValue = getIntValueWithFallback(key, defaultValue, fallbackKey, fallbackDefaultValue);
        addSliderRow(root, titleText, subtitleText, key, initialValue, min, max, suffix);
    }

    void addTextSettingRow(LinearLayout root, String titleText, String subtitleText,
            String key, String defaultValue, String emptyLabel) {
        addTextSettingRow(root, titleText, subtitleText, key, defaultValue, emptyLabel, null, false);
    }

    void addTextSettingRow(LinearLayout root, String titleText, String subtitleText,
            String key, String defaultValue, String emptyLabel, String inputHint, boolean plainTextInput) {
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

        TextView valueView = new TextView(this);
        valueView.setTextColor(colorPrimary);
        valueView.setTextSize(13);
        valueView.setPadding(dp(12), dp(8), dp(12), dp(8));
        valueView.setBackground(roundRect(colorSurfaceSoft, 999));
        valueView.setMaxWidth(dp(180));
        valueView.setSingleLine(false);
        updateTextSettingLabel(valueView, readStringSetting(key, defaultValue), emptyLabel);
        setTapClickListener(valueView, v -> showTextInputDialog(
                titleText,
                readStringSetting(key, defaultValue),
                subtitleText,
                inputHint,
                plainTextInput,
                value -> {
                    putStringSetting(key, value);
                    updateTextSettingLabel(valueView, value, emptyLabel);
                }));

        row.addView(textColumn, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(valueView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(row, matchWrap());
    }

    void addChoiceRow(LinearLayout root, String titleText, String subtitleText,
            String key, int defaultValue, int[] values, String[] labels) {
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

        TextView valueView = new TextView(this);
        valueView.setTextColor(colorPrimary);
        valueView.setTextSize(13);
        valueView.setPadding(dp(12), dp(8), dp(12), dp(8));
        valueView.setBackground(roundRect(colorSurfaceSoft, 999));
        int currentValue = readIntSetting(key, defaultValue);
        valueView.setText(resolveChoiceLabel(currentValue, values, labels));
        setTapClickListener(valueView, v -> showChoiceMenu(v, key, defaultValue, values, labels, valueView));

        row.addView(textColumn, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(valueView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(row, matchWrap());
    }

    void addActionButtonRow(LinearLayout root, String titleText, String subtitleText,
            String buttonText, Runnable action) {
        settingsUiFactory.addActionButtonRow(root, titleText, subtitleText, buttonText, action);
    }

    void applyAllPositionOffsets() {
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : POSITION_TUNING_KEYS) {
            int value = getPendingPositionOffsetValue(
                    key,
                    0,
                    getPositionOffsetMinTenthDp(key),
                    getPositionOffsetMaxTenthDp(key));
            editor.putInt(key, value);
        }
        SettingsStore.markPositionOffsetStorageVersion(editor);
        editor.apply();
        SettingsStore.notifyChanged(this);
        invalidatePreview();
        showToast("个性化位置微调已应用");
    }

    void resetAllPositionOffsets() {
        for (String key : POSITION_TUNING_KEYS) {
            updatePendingPositionOffsetValue(
                    key,
                    0,
                    getPositionOffsetMinTenthDp(key),
                    getPositionOffsetMaxTenthDp(key));
        }
        for (PositionOffsetSliderBinding binding : positionTuningSliderBindings) {
            binding.setValue(0);
        }
        showToast("个性化位置微调已归零为 0.0dp，点应用后写入状态栏");
    }

    void addDivider(LinearLayout root) {
        settingsUiFactory.addDivider(root);
    }

    void addProfileSectionHeader(LinearLayout root, String titleText, String subtitleText) {
        settingsUiFactory.addProfileSectionHeader(root, titleText, subtitleText);
    }

    void showChoiceMenu(View anchor, String key, int defaultValue,
            int[] values, String[] labels, TextView valueView) {
        PopupMenu popup = new PopupMenu(this, anchor);
        int currentValue = readIntSetting(key, defaultValue);
        for (int i = 0; i < values.length && i < labels.length; i++) {
            popup.getMenu().add(0, values[i], i, labels[i]);
        }
        popup.setOnMenuItemClickListener(item -> {
            performTapHaptic(anchor);
            int selectedValue = item.getItemId();
            putIntSetting(key, selectedValue);
            valueView.setText(resolveChoiceLabel(selectedValue, values, labels));
            return true;
        });
        popup.show();
        valueView.setText(resolveChoiceLabel(currentValue, values, labels));
    }

    int readIntSetting(String key, int defaultValue) {
        return SettingsStore.readInt(prefs, key, defaultValue);
    }

    int readPositionOffsetTenthDpSetting(String key, int defaultValue) {
        return SettingsStore.readPositionOffsetTenthDp(prefs, key, defaultValue);
    }

    String resolveChoiceLabel(int value, int[] values, String[] labels) {
        for (int i = 0; i < values.length && i < labels.length; i++) {
            if (values[i] == value) {
                return labels[i];
            }
        }
        return labels.length > 0 ? labels[0] : "";
    }

    String readStringSetting(String key, String defaultValue) {
        return SettingsStore.readString(prefs, key, defaultValue);
    }

    int getIntValueWithFallback(String key, int defaultValue, String fallbackKey, int fallbackDefaultValue) {
        if (prefs.contains(key)) {
            return SettingsStore.readInt(prefs, key, defaultValue);
        }
        return SettingsStore.readInt(prefs, fallbackKey, fallbackDefaultValue);
    }

    void putBooleanSetting(String key, boolean value) {
        prefs.edit().putBoolean(key, value).apply();
        SettingsStore.notifyChanged(this);
        invalidatePreview();
    }

    void putIntSetting(String key, int value) {
        SharedPreferences.Editor editor = prefs.edit().putInt(key, value);
        if (SettingsStore.isPositionOffsetKey(key)) {
            SettingsStore.markPositionOffsetStorageVersion(editor);
        }
        editor.apply();
        SettingsStore.notifyChanged(this);
        invalidatePreview();
    }

    void putStringSetting(String key, String value) {
        prefs.edit().putString(key, value == null ? "" : value).apply();
        SettingsStore.notifyChanged(this);
        invalidatePreview();
    }

    void disableTelephonyDebug() {
        prefs.edit().putBoolean(SettingsStore.KEY_TELEPHONY_DEBUG_ENABLED, false).apply();
        SettingsStore.notifyChanged(this);
        invalidatePreview();
        showToast("已恢复真实 Telephony");
    }

    void testLaunchMBackIntent() {
        String raw = readStringSetting(
                SettingsStore.KEY_MBACK_LONG_TOUCH_INTENT_URI,
                SettingsStore.DEFAULT_MBACK_LONG_TOUCH_INTENT_URI);
        if (TextUtils.isEmpty(raw) || TextUtils.isEmpty(raw.trim())) {
            showToast("请先填写目标 URL 或 Intent URI");
            return;
        }
        try {
            Intent intent;
            String trimmed = raw.trim();
            if (trimmed.startsWith("intent:") || trimmed.contains("#Intent;")) {
                intent = Intent.parseUri(trimmed, Intent.URI_INTENT_SCHEME);
            } else {
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(trimmed));
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            showToast("测试启动已发送");
        } catch (Throwable t) {
            showToast("测试启动失败：" + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()));
        }
    }

    void invalidatePreview() {
    }

    void resetAllSettings() {
        prefs.edit().clear().apply();
        SettingsStore.notifyChanged(this);
        invalidatePreview();
        showToast("\u5df2\u6062\u590d\u9ed8\u8ba4\u914d\u7f6e");
        recreate();
    }

    String formatValue(int value, String suffix) {
        return suffix == null || suffix.length() == 0 ? Integer.toString(value) : value + suffix;
    }

    String formatOffsetValue(int valueTenthDp) {
        int normalized = SettingsStore.normalizeIconYOffsetTenthDp(valueTenthDp);
        float offsetDp = SettingsStore.positionOffsetTenthDpToDp(normalized);
        return String.format(Locale.US, "%s%.1fdp", offsetDp > 0f ? "+" : "", offsetDp);
    }

    int getPositionOffsetMinTenthDp(String key) {
        if (SettingsStore.KEY_CLOCK_RIGHT_PADDING_OFFSET_DP.equals(key)) {
            return SettingsStore.CLOCK_RIGHT_PADDING_OFFSET_MIN_TENTH_DP;
        }
        return POSITION_OFFSET_MIN_TENTH_DP;
    }

    int getPositionOffsetMaxTenthDp(String key) {
        if (SettingsStore.KEY_CLOCK_RIGHT_PADDING_OFFSET_DP.equals(key)) {
            return SettingsStore.CLOCK_RIGHT_PADDING_OFFSET_MAX_TENTH_DP;
        }
        return POSITION_OFFSET_MAX_TENTH_DP;
    }

    String formatInsetValue(int value) {
        return value < 0 ? "系统默认" : value + "dp";
    }

    void startExportConfig() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "flyme_status_bar_sizer_config.json");
        startActivityForResult(intent, REQUEST_EXPORT_CONFIG);
    }

    void startImportConfig() {
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
            root.put("version", 3);
            for (String key : SettingsStore.BOOLEAN_KEYS) {
                if (SettingsStore.includeInBackup(key)) {
                    settings.put(key, SettingsStore.readBoolean(
                            prefs, key, SettingsStore.defaultBoolean(key)));
                }
            }
            for (String key : SettingsStore.INT_KEYS) {
                if (SettingsStore.includeInBackup(key)) {
                    settings.put(key, SettingsStore.readInt(
                            prefs, key, SettingsStore.defaultInt(key)));
                }
            }
            for (String key : SettingsStore.STRING_KEYS) {
                if (SettingsStore.includeInBackup(key)) {
                    settings.put(key, SettingsStore.readString(
                            prefs, key, SettingsStore.defaultString(key)));
                }
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
                showToast("\u5bfc\u5165\u5931\u8d25\uff1a\u914d\u7f6e\u6587\u4ef6\u683c\u5f0f\u4e0d\u6b63\u786e");
                return;
            }
            int version = root.optInt("version", 0);
            if (!"flyme_status_bar_sizer".equals(root.optString("schema"))
                    || (version != 2 && version != 3)) {
                showToast("\u5bfc\u5165\u5931\u8d25\uff1a\u53ea\u652f\u6301 v2 / v3 \u914d\u7f6e\u6587\u4ef6");
                return;
            }
            SharedPreferences.Editor editor = prefs.edit().clear();
            for (String key : SettingsStore.BOOLEAN_KEYS) {
                if (!SettingsStore.includeInBackup(key)) {
                    continue;
                }
                editor.putBoolean(key, settings.optBoolean(key, SettingsStore.defaultBoolean(key)));
            }
            for (String key : SettingsStore.INT_KEYS) {
                if (!SettingsStore.includeInBackup(key)) {
                    continue;
                }
                int value = settings.optInt(key, SettingsStore.defaultInt(key));
                if (version < 3 && SettingsStore.isPositionOffsetKey(key)) {
                    value = SettingsStore.normalizeIconYOffsetTenthDp(value * 10);
                }
                editor.putInt(key, value);
            }
            for (String key : SettingsStore.STRING_KEYS) {
                if (!SettingsStore.includeInBackup(key)) {
                    continue;
                }
                editor.putString(key, settings.optString(key, SettingsStore.defaultString(key)));
            }
            SettingsStore.markPositionOffsetStorageVersion(editor);
            editor.apply();
            SettingsStore.notifyChanged(this);
            invalidatePreview();
            showToast("\u914d\u7f6e\u5df2\u5bfc\u5165");
            recreate();
        } catch (Throwable t) {
            showToast("\u5bfc\u5165\u5931\u8d25\uff1a" + t.getMessage());
        }
    }

    String readText(InputStream input) throws java.io.IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }

    void showIntInputDialog(String titleText, int currentValue, int min, int max, String suffix,
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

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(titleText)
                .setMessage("\u8f93\u5165\u8303\u56f4 " + min + " ~ " + max + (suffix == null ? "" : suffix))
                .setView(input)
                .setNegativeButton("\u53d6\u6d88", null)
                .setPositiveButton("\u786e\u5b9a", (dialogInterface, which) -> {
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
        attachDialogButtonHaptics(dialog);
    }

    void showDecimalInputDialog(String titleText, int currentValueTenthDp,
            int minTenthDp, int maxTenthDp, IntValueConsumer consumer) {
        EditText input = new EditText(this);
        input.setText(String.format(Locale.US, "%.1f",
                SettingsStore.positionOffsetTenthDpToDp(currentValueTenthDp)));
        input.setSelection(input.getText().length());
        input.setInputType((minTenthDp < 0
                ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED
                : InputType.TYPE_CLASS_NUMBER)
                | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint(formatOffsetInputRangeHint(minTenthDp, maxTenthDp));
        int padding = dp(20);
        input.setPadding(padding, padding, padding, padding);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(titleText)
                .setMessage("\u8f93\u5165\u8303\u56f4 "
                        + formatOffsetValue(minTenthDp)
                        + " ~ "
                        + formatOffsetValue(maxTenthDp))
                .setView(input)
                .setNegativeButton("\u53d6\u6d88", null)
                .setPositiveButton("\u786e\u5b9a", (dialogInterface, which) -> {
                    String text = input.getText() == null ? "" : input.getText().toString().trim();
                    if (text.length() == 0) {
                        showToast("\u8bf7\u8f93\u5165\u6570\u503c");
                        return;
                    }
                    try {
                        int value = parseOffsetInputToTenthDp(text);
                        int clamped = Math.max(minTenthDp, Math.min(maxTenthDp, value));
                        consumer.accept(clamped);
                    } catch (NumberFormatException ignored) {
                        showToast("\u8bf7\u8f93\u5165 0.1dp \u7cbe\u5ea6\u7684\u6570\u503c");
                    }
                })
                .show();
        attachDialogButtonHaptics(dialog);
    }

    void showTextInputDialog(String titleText, String currentValue, String message,
            String inputHint, boolean plainTextInput, TextValueConsumer consumer) {
        EditText input = new EditText(this);
        input.setText(currentValue == null ? "" : currentValue);
        input.setSelection(input.getText().length());
        if (plainTextInput) {
            input.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            input.setHint(inputHint == null ? "" : inputHint);
        } else {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
            input.setHint(inputHint == null ? "https://example.com or intent://..." : inputHint);
        }
        input.setMinLines(2);
        input.setMaxLines(6);
        int padding = dp(20);
        input.setPadding(padding, padding, padding, padding);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(titleText)
                .setMessage(message)
                .setView(input)
                .setNeutralButton("清空", (dialogInterface, which) -> consumer.accept(""))
                .setNegativeButton("\u53d6\u6d88", null)
                .setPositiveButton("\u786e\u5b9a", (dialogInterface, which) ->
                        consumer.accept(input.getText() == null ? "" : input.getText().toString().trim()))
                .show();
        attachDialogButtonHaptics(dialog);
    }

    void updateTextSettingLabel(TextView valueView, String value, String emptyLabel) {
        if (TextUtils.isEmpty(value)) {
            valueView.setText(emptyLabel);
            return;
        }
        valueView.setText(value);
    }

    int getPendingIntSliderValue(String key, int defaultValue, int min, int max) {
        Integer pending = pendingIntSliderValues.get(key);
        if (pending != null) {
            return Math.max(min, Math.min(max, pending));
        }
        int clamped = Math.max(min, Math.min(max, readIntSetting(key, defaultValue)));
        pendingIntSliderValues.put(key, clamped);
        return clamped;
    }

    void updatePendingIntSliderValue(String key, int value, int min, int max) {
        pendingIntSliderValues.put(key, Math.max(min, Math.min(max, value)));
    }

    void applyPendingIntSliderValue(String key, int defaultValue, int min, int max, String titleText) {
        int value = getPendingIntSliderValue(key, defaultValue, min, max);
        putIntSetting(key, value);
        showToast(titleText + "已应用");
    }

    void applyPendingPositionOffsetValue(String key, int defaultValueTenthDp,
            int minTenthDp, int maxTenthDp, String titleText) {
        int value = getPendingPositionOffsetValue(key, defaultValueTenthDp, minTenthDp, maxTenthDp);
        putIntSetting(key, value);
        showToast(titleText + "已应用");
    }

    int getPendingPositionOffsetValue(String key, int defaultValue, int min, int max) {
        Integer pending = pendingPositionOffsetValues.get(key);
        if (pending != null) {
            return Math.max(min, Math.min(max, pending));
        }
        int clamped = Math.max(min, Math.min(max, readPositionOffsetTenthDpSetting(key, defaultValue)));
        pendingPositionOffsetValues.put(key, clamped);
        return clamped;
    }

    void updatePendingPositionOffsetValue(String key, int value, int min, int max) {
        pendingPositionOffsetValues.put(key, Math.max(min, Math.min(max, value)));
    }

    int sliderProgressToPositionOffsetTenthDp(int progress, int minTenthDp, int maxTenthDp) {
        int minDp = minTenthDp / 10;
        int maxDp = maxTenthDp / 10;
        int coarseDp = minDp + progress;
        return Math.max(minTenthDp, Math.min(maxTenthDp, coarseDp * 10));
    }

    int positionOffsetTenthDpToSliderProgress(int valueTenthDp, int minTenthDp, int maxTenthDp) {
        int minDp = minTenthDp / 10;
        int maxDp = maxTenthDp / 10;
        int coarseDp = Math.max(minDp, Math.min(maxDp,
                Math.round(SettingsStore.positionOffsetTenthDpToDp(valueTenthDp))));
        return coarseDp - minDp;
    }

    String formatOffsetInputRangeHint(int minTenthDp, int maxTenthDp) {
        return String.format(Locale.US, "%.1f ~ %.1f",
                SettingsStore.positionOffsetTenthDpToDp(minTenthDp),
                SettingsStore.positionOffsetTenthDpToDp(maxTenthDp));
    }

    int parseOffsetInputToTenthDp(String text) {
        String normalized = text == null ? "" : text.trim().replace(',', '.');
        if (normalized.length() == 0) {
            throw new NumberFormatException("empty");
        }
        BigDecimal value = new BigDecimal(normalized);
        BigDecimal scaled = value.multiply(BigDecimal.TEN).setScale(0, RoundingMode.HALF_UP);
        return SettingsStore.normalizeIconYOffsetTenthDp(scaled.intValueExact());
    }

    private String formatSliderDisplayValue(int value, String suffix, boolean insetValue) {
        return insetValue ? formatInsetValue(value) : formatValue(value, suffix);
    }

    void setTapClickListener(View view, View.OnClickListener listener) {
        if (view == null || listener == null) {
            return;
        }
        view.setHapticFeedbackEnabled(true);
        view.setOnClickListener(v -> {
            performTapHaptic(v);
            listener.onClick(v);
        });
    }

    void styleSwitch(Switch toggle) {
        if (toggle == null) {
            return;
        }
        toggle.setShowText(false);
        toggle.setSplitTrack(false);
        toggle.setSwitchMinWidth(dp(52));
        toggle.setMinWidth(dp(52));
        toggle.setTrackTintList(null);
        toggle.setThumbTintList(null);
        toggle.setTrackDrawable(buildSwitchTrackDrawable());
        toggle.setThumbDrawable(buildSwitchThumbDrawable());
    }

    void styleSeekBar(SeekBar seekBar) {
        if (seekBar == null) {
            return;
        }
        ColorStateList activeTint = ColorStateList.valueOf(colorPrimary);
        ColorStateList inactiveTint = ColorStateList.valueOf(colorSurfaceStrong);
        seekBar.setThumbTintList(activeTint);
        seekBar.setProgressTintList(activeTint);
        seekBar.setSecondaryProgressTintList(inactiveTint);
        seekBar.setProgressBackgroundTintList(inactiveTint);
    }

    private StateListDrawable buildSwitchTrackDrawable() {
        StateListDrawable drawable = new StateListDrawable();
        drawable.addState(new int[]{android.R.attr.state_checked},
                buildSwitchTrackShape(colorPrimary));
        drawable.addState(new int[0], buildSwitchTrackShape(colorSurfaceStrong));
        return drawable;
    }

    private StateListDrawable buildSwitchThumbDrawable() {
        StateListDrawable drawable = new StateListDrawable();
        drawable.addState(new int[]{android.R.attr.state_checked},
                buildSwitchThumbShape(Color.WHITE, colorPrimary));
        drawable.addState(new int[0], buildSwitchThumbShape(Color.WHITE, colorStroke));
        return drawable;
    }

    private GradientDrawable buildSwitchTrackShape(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(dp(999));
        drawable.setSize(dp(44), dp(24));
        return drawable;
    }

    private GradientDrawable buildSwitchThumbShape(int color, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        drawable.setStroke(dp(1), strokeColor);
        drawable.setSize(dp(20), dp(20));
        return drawable;
    }

    void performTapHaptic(View view) {
        if (view == null) {
            return;
        }
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    void performSliderHaptic(View view) {
        if (view == null) {
            return;
        }
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
    }

    void attachDialogButtonHaptics(AlertDialog dialog) {
        if (dialog == null) {
            return;
        }
        attachPressHaptic(dialog.getButton(AlertDialog.BUTTON_POSITIVE));
        attachPressHaptic(dialog.getButton(AlertDialog.BUTTON_NEGATIVE));
        attachPressHaptic(dialog.getButton(AlertDialog.BUTTON_NEUTRAL));
    }

    void attachPressHaptic(View view) {
        if (view == null) {
            return;
        }
        view.setHapticFeedbackEnabled(true);
        view.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                performTapHaptic(v);
            }
            return false;
        });
    }

    void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    void restartSystemUi() {
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(homeIntent);
        moveTaskToBack(true);
        new Handler(Looper.getMainLooper()).postDelayed(() ->
                        restartRootCommands("SystemUI", new String[]{
                                "killall " + PACKAGE_SYSTEM_UI,
                                "pkill -f " + PACKAGE_SYSTEM_UI,
                                "am crash " + PACKAGE_SYSTEM_UI
                        }),
                SYSTEM_UI_RESTART_DELAY_MS);
    }

    void restartLauncher() {
        restartPackageProcess(PACKAGE_FLYME_LAUNCHER, "系统桌面");
    }

    private void restartPackageProcess(String packageName, String label) {
        restartRootCommands(label, new String[]{
                "am force-stop " + packageName,
                "pkill -f " + packageName,
                "killall " + packageName
        });
    }

    private void restartRootCommands(String label, String[] commands) {
        showToast("\u6b63\u5728\u91cd\u542f" + label + "...");
        new Thread(() -> {
            boolean success = false;
            String error = null;
            try {
                if (commands != null) {
                    for (String command : commands) {
                        if (command == null || command.trim().length() == 0) {
                            continue;
                        }
                        Process process = new ProcessBuilder("su", "-c", command)
                                .redirectErrorStream(true)
                                .start();
                        String output = readText(process.getInputStream()).trim();
                        int exitCode = process.waitFor();
                        if (exitCode == 0) {
                            success = true;
                            break;
                        }
                        if (output.length() > 0) {
                            error = output;
                        }
                    }
                }
            } catch (Throwable t) {
                error = t.getMessage();
            }
            boolean finalSuccess = success;
            String finalError = error;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (finalSuccess) {
                    showToast(label + "\u5df2\u91cd\u542f");
                } else if (finalError == null || finalError.length() == 0) {
                    showToast("\u91cd\u542f\u5931\u8d25\uff0c\u8bf7\u786e\u8ba4\u5df2\u6388\u4e88 root \u6743\u9650");
                } else {
                    showToast("\u91cd\u542f\u5931\u8d25\uff1a" + finalError);
                }
            });
        }).start();
    }

    LinearLayout card(int color, int radiusDp) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(roundRect(color, radiusDp));
        return card;
    }

    LinearLayout card(int color, int strokeColor, int radiusDp) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(outlinedRect(color, strokeColor, 1, radiusDp));
        return card;
    }

    TextView chip(String text, int backgroundColor, int textColor) {
        return settingsUiFactory.chip(text, backgroundColor, textColor);
    }

    TextView filledButton(String text, int backgroundColor, int textColor) {
        return settingsUiFactory.filledButton(text, backgroundColor, textColor);
    }

    GradientDrawable gradientCard() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{colorPrimaryContainer, colorPrimary, colorPrimaryDeep});
        drawable.setCornerRadius(dp(32));
        return drawable;
    }

    int backgroundColor() {
        return colorBackground;
    }

    int surfaceColor() {
        return colorSurface;
    }

    int surfaceSoftColor() {
        return colorSurfaceSoft;
    }

    int surfaceStrongColor() {
        return colorSurfaceStrong;
    }

    int featureSurfaceColor() {
        return colorFeatureSurface;
    }

    int featureStrokeColor() {
        return colorFeatureStroke;
    }

    int textColor() {
        return colorText;
    }

    int subtextColor() {
        return colorSubtext;
    }

    int primaryColor() {
        return colorPrimary;
    }

    int primaryContainerColor() {
        return colorPrimaryContainer;
    }

    int primaryDeepColor() {
        return colorPrimaryDeep;
    }

    int secondaryColor() {
        return 0xFF006688;
    }

    int tertiaryColor() {
        return 0xFF964500;
    }

    int errorColor() {
        return 0xFFBA1A1A;
    }

    int strokeColor() {
        return colorStroke;
    }

    String githubUrl() {
        return GITHUB_URL;
    }

    String qqGroupUrl() {
        return QQ_GROUP_URL;
    }

    String qqGroupNumber() {
        return QQ_GROUP_NUMBER;
    }

    String supportedScopesSummary() {
        return "android / com.android.systemui / 主流输入法";
    }

    SharedPreferences prefs() {
        return prefs;
    }

    ClockExpressionEditor clockExpressionEditor() {
        return clockExpressionEditor;
    }

    ImeToolbarEditor imeToolbarEditor() {
        return imeToolbarEditor;
    }

    ArrayList<PositionOffsetSliderBinding> positionTuningSliderBindings() {
        return positionTuningSliderBindings;
    }

    void openExternalLink(String url) {
        if (TextUtils.isEmpty(url)) {
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Throwable t) {
            showToast("无法打开链接：" + url);
        }
    }

    View createIconSizingCard() {
        return settingsCardFactory.createIconSizingCard();
    }

    View createBatterySettingsCard() {
        return settingsCardFactory.createBatterySettingsCard();
    }

    View createNotificationSettingsCard() {
        return settingsCardFactory.createNotificationSettingsCard();
    }

    View createSignalSettingsCard() {
        return settingsCardFactory.createSignalSettingsCard();
    }

    View createConnectionRateSettingsCard() {
        return settingsCardFactory.createConnectionRateSettingsCard();
    }

    View createTimeExpressionSettingsCard() {
        return settingsCardFactory.createTimeExpressionSettingsCard();
    }

    View createTimeInteractionSettingsCard() {
        return settingsCardFactory.createTimeInteractionSettingsCard();
    }

    View createTimeTypographySettingsCard() {
        return settingsCardFactory.createTimeTypographySettingsCard();
    }

    View createMBackActionSettingsCard() {
        return settingsCardFactory.createMBackActionSettingsCard();
    }

    View createMBackNavigationSettingsCard() {
        return settingsCardFactory.createMBackNavigationSettingsCard();
    }

    View createImeToolbarSettingsCard() {
        return settingsCardFactory.createImeToolbarSettingsCard();
    }

    View createSystemAppearanceSettingsCard() {
        return settingsCardFactory.createSystemAppearanceSettingsCard();
    }

    View createLauncherRecentsSettingsCard() {
        return settingsCardFactory.createLauncherRecentsSettingsCard();
    }

    View createAdvancedToolsCard() {
        return settingsCardFactory.createAdvancedToolsCard();
    }

    View createConfigManagementCard() {
        return settingsCardFactory.createConfigManagementCard();
    }

    View createPerformanceDebugCard() {
        return settingsCardFactory.createPerformanceDebugCard();
    }

    View createPositionTuningSettingsCard() {
        return settingsCardFactory.createPositionTuningSettingsCard();
    }

    View createTelephonyDebugSettingsCard() {
        return settingsCardFactory.createTelephonyDebugSettingsCard();
    }

    View buildSectionCard(String titleText, String subtitleText, View content) {
        LinearLayout card = card(colorSurface, colorStroke, 28);

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(colorText);
        title.setTextSize(20);
        card.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText(subtitleText);
        subtitle.setTextColor(colorSubtext);
        subtitle.setTextSize(14);
        subtitle.setPadding(0, dp(6), 0, 0);
        card.addView(subtitle, matchWrap());

        if (content != null) {
            card.addView(content, matchWrapWithTop(16));
        }
        return card;
    }

    private final class PositionOffsetSliderBinding {
        private final TextView valueView;
        private final SeekBar seekBar;
        private final int minTenthDp;
        private final int maxTenthDp;

        private PositionOffsetSliderBinding(
                TextView valueView,
                SeekBar seekBar,
                int minTenthDp,
                int maxTenthDp) {
            this.valueView = valueView;
            this.seekBar = seekBar;
            this.minTenthDp = minTenthDp;
            this.maxTenthDp = maxTenthDp;
        }

        private void setValue(int value) {
            int normalized = Math.max(minTenthDp, Math.min(maxTenthDp, value));
            valueView.setText(formatOffsetValue(normalized));
            int progress = positionOffsetTenthDpToSliderProgress(
                    normalized,
                    minTenthDp,
                    maxTenthDp);
            if (seekBar.getProgress() != progress) {
                seekBar.setProgress(progress);
            }
        }
    }

    private interface TextValueConsumer {
        void accept(String value);
    }

    private void initPalette() {
        colorBackground = FALLBACK_BACKGROUND;
        colorSurface = FALLBACK_SURFACE;
        colorSurfaceSoft = FALLBACK_SURFACE_SOFT;
        colorSurfaceStrong = FALLBACK_SURFACE_STRONG;
        colorFeatureSurface = FALLBACK_FEATURE_SURFACE;
        colorFeatureStroke = FALLBACK_FEATURE_STROKE;
        colorText = FALLBACK_TEXT;
        colorSubtext = FALLBACK_SUBTEXT;
        colorPrimary = FALLBACK_PRIMARY;
        colorPrimaryContainer = FALLBACK_PRIMARY_CONTAINER;
        colorPrimaryDeep = FALLBACK_PRIMARY_DEEP;
        colorStroke = FALLBACK_STROKE;
    }

    private void configureSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            getWindow().setStatusBarColor(colorSurface);
            getWindow().setNavigationBarColor(colorSurface);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = getWindow().getDecorView().getSystemUiVisibility();
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            getWindow().getDecorView().setSystemUiVisibility(flags);
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

    GradientDrawable roundRect(int color, int radiusDp) {
        return settingsUiFactory.roundRect(color, radiusDp);
    }

    GradientDrawable outlinedRect(int color, int strokeColor, int strokeWidthDp, int radiusDp) {
        return settingsUiFactory.outlinedRect(color, strokeColor, strokeWidthDp, radiusDp);
    }

    LinearLayout.LayoutParams matchWrap() {
        return settingsUiFactory.matchWrap();
    }

    LinearLayout.LayoutParams matchWrapWithTop(int topDp) {
        return settingsUiFactory.matchWrapWithTop(topDp);
    }

    int dp(int value) {
        return settingsUiFactory.dp(value);
    }

    int statusBarInset() {
        return getStatusBarInset();
    }

    private int getStatusBarInset() {
        int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resId != 0) {
            try {
                return getResources().getDimensionPixelSize(resId);
            } catch (Resources.NotFoundException ignored) {
            }
        }
        return dp(24);
    }

    private interface IntValueConsumer {
        void accept(int value);
    }

}
