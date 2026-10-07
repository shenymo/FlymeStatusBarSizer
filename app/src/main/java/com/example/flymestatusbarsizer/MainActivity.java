package com.example.flymestatusbarsizer;

import com.example.flymestatusbarsizer.config.SettingsStore;
import com.example.flymestatusbarsizer.config.RemoteSettingsSync;
import com.example.flymestatusbarsizer.feature.battery.CircleBatteryAnimationConfig;
import com.example.flymestatusbarsizer.feature.clock.ClockDetailActionGridEditor;
import com.example.flymestatusbarsizer.feature.clock.ClockExpressionEditor;
import com.example.flymestatusbarsizer.feature.ime.ImeToolbarEditor;
import com.example.flymestatusbarsizer.feature.launcher.organizer.LauncherOrganizerPage;
import com.example.flymestatusbarsizer.feature.onemind.OneMindHookPointDetector;
import com.example.flymestatusbarsizer.ui.AboutPageController;
import com.example.flymestatusbarsizer.ui.AdvancedDebugPageController;
import com.example.flymestatusbarsizer.ui.HomePageController;
import com.example.flymestatusbarsizer.ui.IconsBatteryPageController;
import com.example.flymestatusbarsizer.ui.LauncherStackParamsPageController;
import com.example.flymestatusbarsizer.ui.PageViewUtils;
import com.example.flymestatusbarsizer.ui.PositionTuningPageController;
import com.example.flymestatusbarsizer.ui.RestartTarget;
import com.example.flymestatusbarsizer.ui.SettingsCardFactory;
import com.example.flymestatusbarsizer.ui.SettingsUiFactory;
import com.example.flymestatusbarsizer.ui.SystemAppearancePageController;
import com.example.flymestatusbarsizer.ui.SystemInteractionPageController;
import com.example.flymestatusbarsizer.ui.TelephonyDebugPageController;
import com.example.flymestatusbarsizer.ui.TimeNetworkPageController;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.Typeface;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.EditorInfo;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.ScrollView;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final Pattern SEARCH_TEXT_PATTERN = Pattern.compile("[^\\p{L}\\p{N}]");
    private static final int REQUEST_EXPORT_CONFIG = 1001;
    private static final int REQUEST_IMPORT_CONFIG = 1002;

    private static final int MENU_ABOUT = 1;
    public static final String IME_CONTROL_BAR_DRAG_LABEL = "ime_control_bar_button";
    public static final int IME_CONTROL_BAR_POOL_ROW_ITEM_COUNT = 3;
    private static final String PACKAGE_CARLINK = "com.upuphone.carlink";
    private static final String CARLINK_APP_MANAGER =
            "com.upuphone.carlink.settings.activity.AppManaActivity";
    private static final long SYSTEM_UI_RESTART_DELAY_MS = 600L;
    private static final String GITHUB_URL = "https://github.com/shenymo/FlymeStatusBarSizer";
    private static final String QQ_GROUP_URL = "https://qun.qq.com/universal-share/share?ac=1&authKey=WuaHYIEHdI6Y%2Fvn7SvcFMtyuUX%2Bwp%2FMedY0eMgPLq9Bbrz%2FPMRsiIgDttNOMbPWW&busi_data=eyJncm91cENvZGUiOiIxMTAyMTM4MzgxIiwidG9rZW4iOiJIb1hmV2xvaVUxWFk2YjAyOXl5MmIwelljU3A5bFRYejQrb3JtUlJwOXRMK1BLU3pnWWRaSG9VdHZ4M3Fld2xqIiwidWluIjoiMjI4OTU3MTk5MCJ9&data=O3ClX619ry0x93elARpxRoHiwSavPU_N00zhT1jj5d_rR0feICi-g7gudqIpU6sbrKtr1_CCPBpNQ-APojGliw&svctype=4&tempid=h5_group_info";
    private static final String QQ_GROUP_NUMBER = "1102138381";
    public static final Pattern CLOCK_EXPRESSION_TOKEN_PATTERN = Pattern.compile("\\{([A-Za-z0-9_]+)\\}");
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
    private ImageButton backButtonView;
    private ImageButton moreButtonView;
    private TextView topBarEyebrowView;
    private TextView topBarTitleView;
    private TextView topBarSubtitleView;
    private FrameLayout pageHostView;
    private OnBackInvokedCallback systemBackCallback;
    private boolean systemBackCallbackRegistered;
    private final Map<Page, View> pageViews = new LinkedHashMap<>();
    private final ArrayDeque<Page> navigationStack = new ArrayDeque<>();
    private Page currentPage = Page.HOME;
    private View searchHighlightedView;
    private Drawable searchPreviousForeground;
    private final Runnable searchHighlightReset = this::clearSearchHighlight;
    private Runnable refreshSearchResults;
    private LinearLayout restartCardContent;
    private final Handler scopeHandler = new Handler(Looper.getMainLooper());
    private final Runnable scopeServiceListener = () -> scopeHandler.post(this::refreshRestartScope);
    private boolean scopeRefreshActive;
    private int scopeRefreshGeneration;
    private boolean batchRestartRunning;
    private final ClockExpressionEditor clockExpressionEditor = new ClockExpressionEditor(this);
    private final ClockDetailActionGridEditor clockDetailActionGridEditor =
            new ClockDetailActionGridEditor(this);
    private final ImeToolbarEditor imeToolbarEditor = new ImeToolbarEditor(this);
    private final SettingsCardFactory settingsCardFactory = new SettingsCardFactory(this);
    private final SettingsUiFactory settingsUiFactory = new SettingsUiFactory(this);

    public enum Page {
        HOME(null, null, null, false),
        ICONS_BATTERY("图标与电池", "状态栏图标缩放、电池样式、通知图标以及信号与 Wi-Fi 接管设置。", null, true),
        TIME_NETWORK("时间与网络", "实时网速显隐阈值、时间表达式、时钟详情弹窗，以及时间字重字号设置。", null, true),
        SYSTEM_APPEARANCE("系统外观", "Flyme 桌面文件夹、系统界面外观相关设置。", null, true),
        SYSTEM_INTERACTION("系统交互", "MBack 长触、导航栏沉浸与高度、输入法控制栏接管，以及系统桌面后台卡片布局调整。", null, true),
        ADVANCED_DEBUG("高级与调试", "配置管理、WIFI 性能打点，以及高阶工具入口。", null, true),
        ABOUT("关于与支持", "项目地址、交流群、版本构建信息和目标作用域说明。", null, false),
        DONATION("捐赠", null, null, false),
        POSITION_TUNING("布局微调", "单独调整时钟、电池、信号、Wi-Fi 与输入法控制栏的细节位置。", null, true),
        CAMERA_CIRCLE_POSITION("环形摄像头位置微调", "调整摄像头环形电池相对挖孔中心的位置。", null, true),
        LAUNCHER_STACK_PARAMS("堆叠后台参数", "调整 IOS 式堆叠后台的布局、动画、手势、视觉和性能参数。", null, true),
        LAUNCHER_ORGANIZER("AI 整理桌面", "生成分类后预览，确认后应用到 Flyme 桌面。", null, true),
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
            getWindow().setDecorFitsSystemWindows(false);
        }
        setContentView(R.layout.activity_main);
        bindHostViews();
        bindPages();
        showPage(Page.HOME);
    }

    @Override
    protected void onResume() {
        super.onResume();
        scopeRefreshActive = true;
        RemoteSettingsSync.addServiceListener(scopeServiceListener);
        refreshRestartScope();
    }

    @Override
    protected void onPause() {
        scopeRefreshActive = false;
        scopeRefreshGeneration++;
        RemoteSettingsSync.removeServiceListener(scopeServiceListener);
        super.onPause();
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
        scopeRefreshActive = false;
        scopeRefreshGeneration++;
        RemoteSettingsSync.removeServiceListener(scopeServiceListener);
        clearSearchHighlight();
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
        if (com.example.flymestatusbarsizer.feature.assistant.AssistantBackgroundSettings.onResult(
                this, requestCode, uri)) return;
        if (requestCode == REQUEST_EXPORT_CONFIG) {
            exportConfig(uri);
        } else if (requestCode == REQUEST_IMPORT_CONFIG) {
            importConfig(uri);
        }
    }

    private void bindHostViews() {
        View mainRoot = findViewById(R.id.main_root);
        if (mainRoot != null) {
            mainRoot.setBackgroundColor(colorBackground);
            mainRoot.setOnApplyWindowInsetsListener((view, insets) -> {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
                return insets;
            });
            mainRoot.requestApplyInsets();
        }
        topBar = findViewById(R.id.top_bar);
        backButtonView = findViewById(R.id.back_button);
        moreButtonView = findViewById(R.id.more_button);
        topBarEyebrowView = findViewById(R.id.top_bar_eyebrow);
        topBarTitleView = findViewById(R.id.top_bar_title);
        topBarSubtitleView = findViewById(R.id.top_bar_subtitle);
        pageHostView = findViewById(R.id.page_host);
        if (topBar != null) {
            topBar.setBackgroundColor(colorBackground);
        }
        if (pageHostView != null) {
            pageHostView.setBackgroundColor(colorBackground);
        }
        if (backButtonView != null) {
            backButtonView.setBackground(roundRect(Color.TRANSPARENT, 24));
        }
        if (moreButtonView != null) {
            moreButtonView.setBackground(roundRect(Color.TRANSPARENT, 24));
        }
        if (topBarSubtitleView != null) {
            topBarSubtitleView.setBackground(roundRect(Color.TRANSPARENT, 24));
        }
        setTapClickListener(backButtonView, v -> onBackPressed());
        setTapClickListener(moreButtonView, this::showMoreMenu);
    }

    private void bindPages() {
        clearSearchHighlight();
        pageViews.clear();
        registerPage(Page.HOME, R.layout.page_home, HomePageController::bind);
        registerPage(Page.ICONS_BATTERY, R.layout.page_icons_battery, IconsBatteryPageController::bind);
        registerPage(Page.TIME_NETWORK, R.layout.page_time_network, TimeNetworkPageController::bind);
        registerPage(Page.SYSTEM_APPEARANCE, R.layout.page_system_appearance,
                SystemAppearancePageController::bind);
        registerPage(Page.SYSTEM_INTERACTION, R.layout.page_system_interaction,
                SystemInteractionPageController::bind);
        registerPage(Page.ADVANCED_DEBUG, R.layout.page_advanced_debug, AdvancedDebugPageController::bind);
        registerPage(Page.ABOUT, R.layout.page_about, AboutPageController::bind);
        registerPage(Page.DONATION, R.layout.page_donation, null);
        registerPage(Page.POSITION_TUNING, R.layout.page_position_tuning, PositionTuningPageController::bind);
        registerPage(Page.CAMERA_CIRCLE_POSITION, R.layout.page_position_tuning,
                (activity, root) -> root.addView(
                        activity.createCameraCirclePositionCard(), PageViewUtils.matchWrap()));
        registerPage(Page.LAUNCHER_STACK_PARAMS, R.layout.page_launcher_stack_params,
                LauncherStackParamsPageController::bind);
        registerPage(Page.LAUNCHER_ORGANIZER, R.layout.page_system_appearance,
                LauncherOrganizerPage::bind);
        registerPage(Page.TELEPHONY_DEBUG, R.layout.page_telephony_debug, TelephonyDebugPageController::bind);
    }

    private void registerPage(Page page, int layoutResId, PageBinder binder) {
        if (pageHostView == null) {
            return;
        }
        View pageView = getLayoutInflater().inflate(layoutResId, pageHostView, false);
        pageView.setBackgroundColor(colorBackground);
        LinearLayout container = pageView.findViewById(R.id.page_content);
        if (binder != null && container != null) {
            binder.bind(this, container);
        }
        pageView.setVisibility(View.GONE);
        pageHostView.addView(pageView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        pageViews.put(page, pageView);
        addSearchItem(pageView, page.title, page.subtitle);
    }

    public void addSearchItem(View view, String title, String description) {
        if (!TextUtils.isEmpty(title)) {
            view.setTag(R.id.feature_search_metadata, new String[]{title, description});
        }
    }

    public LinearLayout addFeatureSearch(LinearLayout root) {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackground(outlinedRect(colorSurface, colorStroke, 1, 16));
        bar.setClipToOutline(true);
        EditText input = new EditText(this);
        input.setId(R.id.feature_search_input);
        input.setHint("搜索功能，如电池、网速");
        input.setContentDescription("搜索功能");
        input.setTextSize(16);
        input.setTextColor(colorText);
        input.setHintTextColor(colorSubtext);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setBackground(null);
        input.setPadding(dp(12), dp(8), dp(8), dp(8));
        input.setMinHeight(dp(48));
        input.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_feature_search, 0, 0, 0);
        input.setCompoundDrawablePadding(dp(8));
        input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        bar.addView(input, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView clear = new TextView(this);
        clear.setText("×");
        clear.setTextSize(24);
        clear.setTextColor(colorSubtext);
        clear.setGravity(Gravity.CENTER);
        clear.setContentDescription("清除搜索");
        clear.setVisibility(View.GONE);
        setTapClickListener(clear, v -> input.setText(""));
        bar.addView(clear, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(bar, matchWrapWithTop(12));

        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        results.setVisibility(View.GONE);
        root.addView(results, matchWrapWithTop(8));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, matchWrap());
        refreshSearchResults = () -> updateFeatureSearch(input, results, content);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                clear.setVisibility(s.length() == 0 ? View.GONE : View.VISIBLE);
                updateFeatureSearch(input, results, content);
            }
            @Override public void afterTextChanged(Editable text) { }
        });
        input.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEARCH) return false;
            getSystemService(InputMethodManager.class).hideSoftInputFromWindow(input.getWindowToken(), 0);
            return true;
        });
        root.setFocusableInTouchMode(true);
        root.requestFocus();
        return content;
    }

    private void updateFeatureSearch(EditText input, LinearLayout results, LinearLayout content) {
        String query = input.getText().toString().trim();
        boolean searching = !normalizeSearchText(query).isEmpty();
        content.setVisibility(searching ? View.GONE : View.VISIBLE);
        results.setVisibility(searching ? View.VISIBLE : View.GONE);
        results.removeAllViews();
        if (!searching) return;
        ArrayList<SearchResult> matches = new ArrayList<>();
        for (Map.Entry<Page, View> page : pageViews.entrySet()) {
            collectSearchResults(page.getValue(), page.getKey(),
                    page.getKey() == Page.HOME ? "首页" : page.getKey().title, query, matches);
        }
        matches.sort((a, b) -> Integer.compare(a.rank, b.rank));
        TextView count = new TextView(this);
        count.setText(matches.isEmpty() ? "未找到相关功能，试试其他关键词" : "找到 " + matches.size() + " 项功能");
        count.setTextColor(colorSubtext);
        count.setTextSize(13);
        results.addView(count, matchWrap());
        for (SearchResult match : matches) {
            LinearLayout row = card(colorSurface, 16);
            TextView title = new TextView(this);
            title.setText(match.title);
            title.setTextColor(colorText);
            title.setTextSize(16);
            row.addView(title, matchWrap());
            TextView path = new TextView(this);
            path.setText(match.path);
            path.setTextColor(colorSubtext);
            path.setTextSize(12);
            row.addView(path, matchWrapWithTop(4));
            setTapClickListener(row, v -> showSearchResult(input, match));
            results.addView(row, matchWrapWithTop(8));
        }
    }

    private void collectSearchResults(View view, Page page, String path, String query,
            ArrayList<SearchResult> matches) {
        Object category = view.getTag(R.id.feature_search_category);
        if (category instanceof String) path += " › " + category;
        Object metadata = view.getTag(R.id.feature_search_metadata);
        if (metadata instanceof String[]) {
            String[] text = (String[]) metadata;
            int rank = searchRank(text[0], text[1], path, query);
            if (rank >= 0) matches.add(new SearchResult(view, page, text[0], path, rank));
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectSearchResults(group.getChildAt(i), page, path, query, matches);
            }
        }
    }

    static String normalizeSearchText(String text) {
        return text == null ? "" : SEARCH_TEXT_PATTERN.matcher(text.toLowerCase(Locale.ROOT)).replaceAll("");
    }

    static int searchRank(String title, String description, String path, String query) {
        String name = normalizeSearchText(title);
        String details = name + " " + normalizeSearchText(description);
        String all = details + " " + normalizeSearchText(path);
        boolean titleMatch = true;
        boolean detailMatch = true;
        if (normalizeSearchText(query).isEmpty()) return -1;
        for (String word : query.trim().split("\\s+")) {
            String token = normalizeSearchText(word);
            if (!all.contains(token)) return -1;
            titleMatch &= name.contains(token);
            detailMatch &= details.contains(token);
        }
        if (name.equals(normalizeSearchText(query))) return 0;
        return titleMatch ? 1 : detailMatch ? 2 : 3;
    }

    private void showSearchResult(EditText input, SearchResult result) {
        View page = pageViews.get(result.page);
        View ancestor = result.target;
        while (ancestor != page && ancestor.getParent() instanceof View) {
            ancestor = (View) ancestor.getParent();
        }
        if (ancestor != page) {
            refreshSearchResults.run();
            showToast("功能列表已更新，请重新选择");
            return;
        }
        input.clearFocus();
        getSystemService(InputMethodManager.class).hideSoftInputFromWindow(input.getWindowToken(), 0);
        if (result.page == Page.HOME) input.setText("");
        openPage(result.page);
        for (View node = result.target; node != page; ) {
            Object expand = node.getTag(R.id.feature_search_expand);
            if (expand instanceof Runnable) ((Runnable) expand).run();
            if (!(node.getParent() instanceof View)) break;
            node = (View) node.getParent();
        }
        View target = result.target;
        View hidden = null;
        for (View node = target; node != page; node = (View) node.getParent()) {
            if (node.getVisibility() != View.VISIBLE) hidden = node;
        }
        if (hidden != null) {
            ViewGroup parent = (ViewGroup) hidden.getParent();
            target = parent;
            String prerequisite = null;
            for (int i = parent.indexOfChild(hidden) - 1; i >= 0 && prerequisite == null; i--) {
                View sibling = parent.getChildAt(i);
                Object metadata = sibling.getTag(R.id.feature_search_metadata);
                if (!(metadata instanceof String[]) || !(sibling instanceof ViewGroup)
                        || sibling.getVisibility() != View.VISIBLE) continue;
                ViewGroup row = (ViewGroup) sibling;
                for (int j = 0; j < row.getChildCount(); j++) {
                    if (row.getChildAt(j) instanceof Switch) {
                        target = sibling;
                        prerequisite = ((String[]) metadata)[0];
                        break;
                    }
                }
            }
            showToast(prerequisite == null ? "该选项暂未显示，请先检查本分类的开关"
                    : "请先开启「" + prerequisite + "」");
        }
        View destination = target;
        page.post(() -> {
            if (currentPage != result.page || !destination.isAttachedToWindow()) return;
            if (page instanceof ScrollView) {
                ScrollView scroll = (ScrollView) page;
                Rect bounds = new Rect();
                destination.getDrawingRect(bounds);
                scroll.offsetDescendantRectToMyCoords(destination, bounds);
                scroll.smoothScrollTo(0, destination == page ? 0 : Math.max(0, bounds.top - dp(12)));
            }
            clearSearchHighlight();
            if (destination != page) {
                searchHighlightedView = destination;
                searchPreviousForeground = destination.getForeground();
                destination.setForeground(outlinedRect(0x186750A4, colorPrimary, 1, 12));
                destination.postDelayed(searchHighlightReset, 1400);
            }
        });
    }

    private void clearSearchHighlight() {
        if (searchHighlightedView == null) return;
        searchHighlightedView.removeCallbacks(searchHighlightReset);
        searchHighlightedView.setForeground(searchPreviousForeground);
        searchHighlightedView = null;
        searchPreviousForeground = null;
    }

    private static final class SearchResult {
        final View target;
        final Page page;
        final String title;
        final String path;
        final int rank;

        SearchResult(View target, Page page, String title, String path, int rank) {
            this.target = target;
            this.page = page;
            this.title = title;
            this.path = path;
            this.rank = rank;
        }
    }

    public void openPage(Page page) {
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
        if (page == Page.HOME) {
            if (refreshSearchResults != null) refreshSearchResults.run();
            refreshRestartScope();
        }
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
            topBarTitleView.setTextSize(20f);
            topBarTitleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        if (topBarSubtitleView != null) {
            if (TextUtils.isEmpty(page.subtitle)) {
                topBarSubtitleView.setVisibility(View.GONE);
            } else {
                topBarSubtitleView.setText("?");
                topBarSubtitleView.setContentDescription(page.title + "说明");
                topBarSubtitleView.setVisibility(View.VISIBLE);
                setTapClickListener(topBarSubtitleView,
                        v -> showHelpDialog(page.title, page.subtitle));
            }
        }
    }

    private void showMoreMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, MENU_ABOUT, 0, "关于与支持");
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == MENU_ABOUT) {
                performTapHaptic(anchor);
                openPage(Page.ABOUT);
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

    public void showTelephonyDebugPage() {
        openPage(Page.TELEPHONY_DEBUG);
    }

    public void showPositionTuningPage() {
        openPage(Page.POSITION_TUNING);
    }

    public void showCameraCirclePositionPage() {
        openPage(Page.CAMERA_CIRCLE_POSITION);
    }

    public void showLauncherStackParamsPage() {
        openPage(Page.LAUNCHER_STACK_PARAMS);
    }

    public void showLauncherOrganizerPage() {
        openPage(Page.LAUNCHER_ORGANIZER);
    }

    public void showClockDetailActionGridEditor() {
        clockDetailActionGridEditor.show();
    }

    public Switch addSwitchRow(LinearLayout root, String titleText, String subtitleText,
            String key, boolean defaultValue) {
        return addSwitchRow(root, titleText, subtitleText, key, defaultValue, null);
    }

    public Switch addSwitchRow(LinearLayout root, String titleText, String subtitleText,
            String key, boolean defaultValue, CompoundButton.OnCheckedChangeListener extraListener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(48));

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(colorText);
        title.setTextSize(16);
        textColumn.addView(title, matchWrap());

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
        addHelpButton(row, titleText, subtitleText);
        toggle.setContentDescription(titleText);
        row.addView(toggle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        setTapClickListener(row, v -> {
            if (toggle.isEnabled()) {
                toggle.setChecked(!toggle.isChecked());
            }
        });
        root.addView(row, matchWrap());
        return toggle;
    }

    LinearLayout buildBatteryHollowOptions() {
        LinearLayout card = card(colorSurfaceSoft, 22);
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

    public void addSliderRow(LinearLayout root, String titleText, String subtitleText, String key,
            int defaultValue, int min, int max, String suffix) {
        addSliderRow(root, titleText, subtitleText, key, defaultValue, min, max, suffix, false);
    }

    public void addTenthDpSliderRow(LinearLayout root, String titleText, String subtitleText, String key,
            int defaultValueTenthDp, int minTenthDp, int maxTenthDp) {
        addSliderRow(root, titleText, subtitleText, key, defaultValueTenthDp,
                minTenthDp, maxTenthDp, "dp", true);
    }

    private void addSliderRow(LinearLayout root, String titleText, String subtitleText, String key,
            int defaultValue, int min, int max, String suffix, boolean tenthDp) {
        int decimalScale = SettingsStore.isCameraCircleBatteryOffsetKey(key) ? 100 : 10;
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
        addHelpButton(header, titleText, subtitleText);

        TextView valueView = new TextView(this);
        valueView.setTextColor(colorPrimary);
        valueView.setTextSize(14);
        int current = readIntSetting(key, defaultValue);
        int clamped = Math.max(min, Math.min(max, current));
        valueView.setText(tenthDp ? formatDpValue(clamped, decimalScale) : formatValue(clamped, suffix));
        valueView.setPadding(dp(10), dp(4), dp(10), dp(4));
        valueView.setBackground(roundRect(colorSurfaceSoft, 16));
        valueView.setMinHeight(dp(36));
        valueView.setGravity(Gravity.CENTER);
        header.addView(valueView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        SeekBar seekBar = new SeekBar(this);
        styleSeekBar(seekBar);
        seekBar.setMax(max - min);
        seekBar.setProgress(clamped - min);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = min + progress;
                valueView.setText(tenthDp ? formatDpValue(value, decimalScale) : formatValue(value, suffix));
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
        if (tenthDp) {
            setTapClickListener(valueView, v -> showDecimalInputDialog(
                    titleText,
                    min + seekBar.getProgress(),
                    min,
                    max,
                    decimalScale,
                    value -> {
                        valueView.setText(formatDpValue(value, decimalScale));
                        seekBar.setProgress(value - min);
                        putIntSetting(key, value);
                    }));
        } else {
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
        }

        row.addView(header, matchWrap());
        row.addView(seekBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(40)));
        root.addView(row, matchWrap());
    }

    public void addPositionOffsetSliderRow(LinearLayout root, String titleText, String subtitleText,
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
        addHelpButton(header, titleText, subtitleText);

        TextView valueView = new TextView(this);
        valueView.setTextColor(colorPrimary);
        valueView.setTextSize(14);
        valueView.setPadding(dp(10), dp(4), dp(10), dp(4));
        valueView.setBackground(roundRect(colorSurfaceSoft, 16));
        valueView.setMinHeight(dp(36));
        valueView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        header.addView(valueView, valueLp);

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
        row.addView(seekBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(40)));
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
        addHelpButton(header, titleText, subtitleText);

        TextView valueView = new TextView(this);
        valueView.setTextColor(colorPrimary);
        valueView.setTextSize(14);
        valueView.setPadding(dp(10), dp(4), dp(10), dp(4));
        valueView.setBackground(roundRect(colorSurfaceSoft, 16));
        valueView.setMinHeight(dp(36));
        valueView.setGravity(Gravity.CENTER);
        header.addView(valueView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

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

        TextView applyButton = filledButton("应用", colorPrimary, Color.WHITE);
        setTapClickListener(applyButton,
                v -> applyPendingPositionOffsetValue(
                        key,
                        defaultValueTenthDp,
                        minTenthDp,
                        maxTenthDp,
                        titleText));
        LinearLayout.LayoutParams applyLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        applyLp.leftMargin = dp(8);
        header.addView(applyButton, applyLp);

        row.addView(header, matchWrap());
        row.addView(seekBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(40)));
        root.addView(row, matchWrap());
    }

    public void addApplySliderRow(LinearLayout root, String titleText, String subtitleText, String key,
            int defaultValue, int min, int max, String suffix) {
        addApplySliderRowInternal(root, titleText, subtitleText, key, defaultValue, min, max, suffix, false);
    }

    public void addApplyInsetSliderRow(LinearLayout root, String titleText, String subtitleText,
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
        addHelpButton(header, titleText, subtitleText);

        TextView valueView = new TextView(this);
        valueView.setTextColor(colorPrimary);
        valueView.setTextSize(14);
        valueView.setPadding(dp(10), dp(4), dp(10), dp(4));
        valueView.setBackground(roundRect(colorSurfaceSoft, 16));
        valueView.setMinHeight(dp(36));
        valueView.setGravity(Gravity.CENTER);
        int clamped = getPendingIntSliderValue(key, defaultValue, min, max);
        valueView.setText(formatSliderDisplayValue(clamped, suffix, insetValue));
        LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        header.addView(valueView, valueLp);

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

        TextView applyButton = filledButton("应用", colorPrimary, Color.WHITE);
        setTapClickListener(applyButton,
                v -> applyPendingIntSliderValue(key, defaultValue, min, max, titleText));
        LinearLayout.LayoutParams applyLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        applyLp.leftMargin = dp(8);
        header.addView(applyButton, applyLp);

        row.addView(header, matchWrap());
        row.addView(seekBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(40)));
        root.addView(row, matchWrap());
    }

    void addSliderRowWithFallback(LinearLayout root, String titleText, String subtitleText, String key,
            int defaultValue, String fallbackKey, int fallbackDefaultValue, int min, int max, String suffix) {
        int initialValue = getIntValueWithFallback(key, defaultValue, fallbackKey, fallbackDefaultValue);
        addSliderRow(root, titleText, subtitleText, key, initialValue, min, max, suffix);
    }

    public void addDefaultableSliderRow(LinearLayout root, String titleText, String subtitleText,
            String key, int defaultValue, int min, int max, String suffix) {
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
        addHelpButton(header, titleText, subtitleText);

        TextView valueView = new TextView(this);
        valueView.setTextColor(colorPrimary);
        valueView.setTextSize(14);
        valueView.setPadding(dp(10), dp(4), dp(10), dp(4));
        valueView.setBackground(roundRect(colorSurfaceSoft, 16));
        valueView.setMinHeight(dp(36));
        valueView.setGravity(Gravity.CENTER);
        int clamped = Math.max(min, Math.min(max, SettingsStore.normalizeLauncherStackParameter(
                key, readIntSetting(key, defaultValue))));
        valueView.setText(formatValue(clamped, suffix));
        header.addView(valueView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView resetButton = filledButton("默认", colorSurfaceStrong, colorText);
        LinearLayout.LayoutParams resetLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        resetLp.leftMargin = dp(8);
        header.addView(resetButton, resetLp);

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
        setTapClickListener(resetButton, v -> {
            int value = Math.max(min, Math.min(max,
                    SettingsStore.normalizeLauncherStackParameter(key, defaultValue)));
            valueView.setText(formatValue(value, suffix));
            seekBar.setProgress(value - min);
            putIntSetting(key, value);
            showToast(titleText + "已恢复默认");
        });

        row.addView(header, matchWrap());
        row.addView(seekBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(40)));
        root.addView(row, matchWrap());
    }

    public void addTextSettingRow(LinearLayout root, String titleText, String subtitleText,
            String key, String defaultValue, String emptyLabel) {
        addTextSettingRow(root, titleText, subtitleText, key, defaultValue, emptyLabel, null, false);
    }

    public TextView addTextSettingRow(LinearLayout root, String titleText, String subtitleText,
            String key, String defaultValue, String emptyLabel, String inputHint, boolean plainTextInput) {
        return addTextSettingRow(root, titleText, subtitleText, key, defaultValue, emptyLabel,
                inputHint, plainTextInput, "清空", "");
    }

    public TextView addTextSettingRow(LinearLayout root, String titleText, String subtitleText,
            String key, String defaultValue, String emptyLabel, String inputHint, boolean plainTextInput,
            String neutralButtonText, String neutralValue) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(48));

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.HORIZONTAL);
        textColumn.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(colorText);
        title.setTextSize(16);
        textColumn.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        addHelpButton(textColumn, titleText, subtitleText);

        TextView valueView = new TextView(this);
        valueView.setTextColor(colorPrimary);
        valueView.setTextSize(13);
        valueView.setPadding(dp(10), dp(4), dp(10), dp(4));
        valueView.setBackground(roundRect(colorSurfaceSoft, 16));
        valueView.setMinHeight(dp(36));
        valueView.setGravity(Gravity.CENTER_VERTICAL);
        valueView.setMaxLines(2);
        valueView.setEllipsize(TextUtils.TruncateAt.END);
        updateTextSettingLabel(valueView, readStringSetting(key, defaultValue), emptyLabel);
        setTapClickListener(valueView, v -> showTextInputDialog(
                titleText,
                readStringSetting(key, defaultValue),
                subtitleText,
                inputHint,
                plainTextInput,
                neutralButtonText,
                neutralValue,
                value -> {
                    putStringSetting(key, value);
                    updateTextSettingLabel(valueView, value, emptyLabel);
                }));

        row.addView(textColumn, matchWrap());
        row.addView(valueView, matchWrapWithTop(4));
        root.addView(row, matchWrap());
        return valueView;
    }

    public void addChoiceRow(LinearLayout root, String titleText, String subtitleText,
            String key, int defaultValue, int[] values, String[] labels) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(48));

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(colorText);
        title.setTextSize(16);
        textColumn.addView(title, matchWrap());

        TextView valueView = new TextView(this);
        valueView.setTextColor(colorPrimary);
        valueView.setTextSize(13);
        valueView.setPadding(dp(10), dp(4), dp(10), dp(4));
        valueView.setBackground(roundRect(colorSurfaceSoft, 16));
        valueView.setMinHeight(dp(36));
        valueView.setGravity(Gravity.CENTER);
        int currentValue = readIntSetting(key, defaultValue);
        valueView.setText(resolveChoiceLabel(currentValue, values, labels));
        setTapClickListener(valueView, v -> showChoiceMenu(v, key, defaultValue, values, labels, valueView));

        row.addView(textColumn, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        addHelpButton(row, titleText, subtitleText);
        row.addView(valueView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(row, matchWrap());
    }

    public void addMultiChoiceRow(LinearLayout root, String titleText, String subtitleText,
            String key, int defaultValue, int[] values, String[] labels, String emptyLabel) {
        settingsUiFactory.addMultiChoiceRow(root, titleText, subtitleText,
                key, defaultValue, values, labels, emptyLabel);
    }

    public TextView addActionButtonRow(LinearLayout root, String titleText, String subtitleText,
            String buttonText, Runnable action) {
        return settingsUiFactory.addActionButtonRow(root, titleText, subtitleText, buttonText, action);
    }

    public void applyAllPositionOffsets() {
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

    public void resetAllPositionOffsets() {
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

    public void addDivider(LinearLayout root) {
        settingsUiFactory.addDivider(root);
    }

    public void addProfileSectionHeader(LinearLayout root, String titleText, String subtitleText) {
        settingsUiFactory.addProfileSectionHeader(root, titleText, subtitleText);
    }

    public void addHelpButton(LinearLayout row, String titleText, String message) {
        settingsUiFactory.addHelpButton(row, titleText, message);
    }

    public void showHelpDialog(String titleText, String message) {
        if (TextUtils.isEmpty(message)) {
            return;
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(titleText)
                .setMessage(message)
                .setPositiveButton("知道了", null)
                .show();
        styleDialog(dialog);
        attachDialogButtonHaptics(dialog);
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

    public String readStringSetting(String key, String defaultValue) {
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

    public void putIntSetting(String key, int value) {
        SharedPreferences.Editor editor = prefs.edit().putInt(key, value);
        if (SettingsStore.isPositionOffsetKey(key)) {
            SettingsStore.markPositionOffsetStorageVersion(editor);
        }
        editor.apply();
        SettingsStore.notifyChanged(this);
        invalidatePreview();
    }

    public void putStringSetting(String key, String value) {
        prefs.edit().putString(key, value == null ? "" : value).apply();
        SettingsStore.notifyChanged(this);
        invalidatePreview();
    }

    public void resetLauncherStackParams() {
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : SettingsStore.LAUNCHER_STACK_PARAMETER_KEYS) {
            editor.putInt(key, SettingsStore.defaultInt(key));
        }
        editor.apply();
        SettingsStore.notifyChanged(this);
        invalidatePreview();
        showToast("堆叠后台参数已恢复默认");
        recreate();
    }

    public void exportLauncherStackParamsToClipboard() {
        try {
            JSONObject root = new JSONObject();
            JSONObject settings = new JSONObject();
            root.put("schema", "flyme_status_bar_sizer_stack_recents");
            root.put("version", 1);
            for (String key : SettingsStore.LAUNCHER_STACK_PARAMETER_KEYS) {
                settings.put(key, SettingsStore.normalizeLauncherStackParameter(
                        key,
                        readIntSetting(key, SettingsStore.defaultInt(key))));
            }
            root.put("settings", settings);
            ClipboardManager clipboard =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText(
                        "launcher_stack_params",
                        root.toString(2)));
            }
            showTextInputDialog(
                    "导出堆叠后台参数",
                    root.toString(2),
                    "已复制到剪贴板。",
                    "",
                    true,
                    "关闭",
                    root.toString(2),
                    value -> {
                    });
        } catch (Throwable t) {
            showToast("导出失败：" + t.getMessage());
        }
    }

    public void showImportLauncherStackParamsDialog() {
        showTextInputDialog(
                "导入堆叠后台参数",
                readClipboardText(),
                "粘贴导出的堆叠后台参数 JSON。",
                "{\"schema\":\"flyme_status_bar_sizer_stack_recents\"...}",
                true,
                "清空",
                "",
                this::importLauncherStackParams);
    }

    private void importLauncherStackParams(String text) {
        try {
            JSONObject root = new JSONObject(text == null ? "" : text.trim());
            if (!"flyme_status_bar_sizer_stack_recents".equals(root.optString("schema"))
                    || root.optInt("version", 0) != 1) {
                showToast("导入失败：格式不正确");
                return;
            }
            JSONObject settings = root.optJSONObject("settings");
            if (settings == null) {
                showToast("导入失败：缺少 settings");
                return;
            }
            int count = 0;
            SharedPreferences.Editor editor = prefs.edit();
            for (String key : SettingsStore.LAUNCHER_STACK_PARAMETER_KEYS) {
                if (!settings.has(key)) {
                    continue;
                }
                editor.putInt(key, SettingsStore.normalizeLauncherStackParameter(
                        key,
                        settings.optInt(key, SettingsStore.defaultInt(key))));
                count++;
            }
            if (count <= 0) {
                showToast("导入失败：没有可用参数");
                return;
            }
            editor.apply();
            SettingsStore.notifyChanged(this);
            invalidatePreview();
            showToast("堆叠后台参数已导入");
            recreate();
        } catch (Throwable t) {
            showToast("导入失败：" + t.getMessage());
        }
    }

    private String readClipboardText() {
        try {
            ClipboardManager clipboard =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null || !clipboard.hasPrimaryClip()
                    || clipboard.getPrimaryClip() == null
                    || clipboard.getPrimaryClip().getItemCount() <= 0) {
                return "";
            }
            CharSequence text = clipboard.getPrimaryClip().getItemAt(0).coerceToText(this);
            return text == null ? "" : text.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    public void disableTelephonyDebug() {
        prefs.edit().putBoolean(SettingsStore.KEY_TELEPHONY_DEBUG_ENABLED, false).apply();
        SettingsStore.notifyChanged(this);
        invalidatePreview();
        showToast("已恢复真实 Telephony");
    }

    public void testLaunchMBackIntent() {
        testLaunchIntentSetting(
                SettingsStore.KEY_MBACK_LONG_TOUCH_INTENT_URI,
                SettingsStore.DEFAULT_MBACK_LONG_TOUCH_INTENT_URI);
    }

    public void testLaunchWindowModeSideGestureIntent() {
        testLaunchIntentSetting(
                SettingsStore.KEY_WINDOWMODE_SIDE_GESTURE_INTENT_URI,
                SettingsStore.DEFAULT_WINDOWMODE_SIDE_GESTURE_INTENT_URI,
                false);
    }

    public void openCarLinkAppManagement() {
        try {
            Intent intent = new Intent();
            intent.setClassName(PACKAGE_CARLINK, CARLINK_APP_MANAGER);
            intent.setData(Uri.parse("carlink://com.upuphone.carlink/linksettings"));
            intent.putExtra("carId", "");
            startActivity(intent);
        } catch (Throwable t) {
            showToast("无法打开 CarLink 应用管理");
        }
    }

    public void testLaunchLauncherAicyTarget() {
        testLaunchIntentSetting(
                SettingsStore.KEY_LAUNCHER_AICY_ENTRY_TARGET,
                SettingsStore.DEFAULT_LAUNCHER_AICY_ENTRY_TARGET,
                true);
    }

    private void testLaunchIntentSetting(String key, String defaultValue) {
        testLaunchIntentSetting(key, defaultValue, false);
    }

    private void testLaunchIntentSetting(String key, String defaultValue, boolean allowPackageName) {
        String raw = readStringSetting(
                key,
                defaultValue);
        if (TextUtils.isEmpty(raw) || TextUtils.isEmpty(raw.trim())) {
            showToast("请先填写目标 URL 或 Intent URI");
            return;
        }
        try {
            Intent intent;
            String trimmed = raw.trim();
            if (trimmed.startsWith("intent:") || trimmed.contains("#Intent;")) {
                intent = Intent.parseUri(trimmed, Intent.URI_INTENT_SCHEME);
            } else if (allowPackageName
                    && trimmed.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")) {
                if (Build.VERSION.SDK_INT >= 33) {
                    getPackageManager()
                            .getLaunchIntentSenderForPackage(trimmed)
                            .sendIntent(this, 0, null, null, null);
                    showToast("测试启动已发送");
                    return;
                }
                intent = getPackageManager().getLaunchIntentForPackage(trimmed);
                if (intent == null) {
                    throw new IllegalArgumentException("该应用没有可启动的主界面");
                }
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

    public void invalidatePreview() {
    }

    public void resetAllSettings() {
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
        float offsetDp = SettingsStore.positionOffsetTenthDpToDp(valueTenthDp);
        return String.format(Locale.US, "%s%.1fdp", offsetDp > 0f ? "+" : "", offsetDp);
    }

    String formatDpValue(int value, int scale) {
        float offsetDp = value / (float) scale;
        return String.format(Locale.US, scale == 100 ? "%s%.2fdp" : "%s%.1fdp",
                offsetDp > 0f ? "+" : "", offsetDp);
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

    public void startExportConfig() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "flyme_status_bar_sizer_config.json");
        startActivityForResult(intent, REQUEST_EXPORT_CONFIG);
    }

    public void startImportConfig() {
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
            root.put("version", 4);
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
                    || (version != 2 && version != 3 && version != 4)) {
                showToast("\u5bfc\u5165\u5931\u8d25\uff1a\u53ea\u652f\u6301 v2 / v3 / v4 \u914d\u7f6e\u6587\u4ef6");
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
                if ((version < 3 && SettingsStore.isPositionOffsetKey(key))
                        || (version < 4 && SettingsStore.isCameraCircleBatteryOffsetKey(key))) {
                    value = SettingsStore.normalizePositionOffsetTenthDp(key, value * 10);
                }
                editor.putInt(key, value);
            }
            for (String key : SettingsStore.STRING_KEYS) {
                if (!SettingsStore.includeInBackup(key)) {
                    continue;
                }
                editor.putString(key, settings.optString(key, SettingsStore.defaultString(key)));
            }
            // Old backups have no animation keys: preserve their static/rainbow behavior.
            if (!settings.has(CircleBatteryAnimationConfig.VERSION)) {
                boolean rainbow = settings.optBoolean(SettingsStore.KEY_CAMERA_CIRCLE_BATTERY_TINT_ENABLED, false);
                for (String key : CircleBatteryAnimationConfig.INT_KEYS) {
                    editor.putInt(key, CircleBatteryAnimationConfig.migratedInt(key, true, rainbow));
                }
                for (String key : CircleBatteryAnimationConfig.BOOLEAN_KEYS) {
                    editor.putBoolean(key, CircleBatteryAnimationConfig.migratedBoolean(key, true, rainbow));
                }
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
        styleDialog(dialog);
        attachDialogButtonHaptics(dialog);
    }

    void showDecimalInputDialog(String titleText, int currentValueTenthDp,
            int minTenthDp, int maxTenthDp, IntValueConsumer consumer) {
        showDecimalInputDialog(titleText, currentValueTenthDp, minTenthDp, maxTenthDp, 10, consumer);
    }

    void showDecimalInputDialog(String titleText, int currentValue,
            int min, int max, int scale, IntValueConsumer consumer) {
        EditText input = new EditText(this);
        input.setText(String.format(Locale.US, scale == 100 ? "%.2f" : "%.1f",
                currentValue / (float) scale));
        input.setSelection(input.getText().length());
        input.setInputType((min < 0
                ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED
                : InputType.TYPE_CLASS_NUMBER)
                | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint(String.format(Locale.US, scale == 100 ? "%.2f ~ %.2f" : "%.1f ~ %.1f",
                min / (float) scale, max / (float) scale));
        int padding = dp(20);
        input.setPadding(padding, padding, padding, padding);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(titleText)
                .setMessage("\u8f93\u5165\u8303\u56f4 "
                        + formatDpValue(min, scale)
                        + " ~ "
                        + formatDpValue(max, scale))
                .setView(input)
                .setNegativeButton("\u53d6\u6d88", null)
                .setPositiveButton("\u786e\u5b9a", (dialogInterface, which) -> {
                    String text = input.getText() == null ? "" : input.getText().toString().trim();
                    if (text.length() == 0) {
                        showToast("\u8bf7\u8f93\u5165\u6570\u503c");
                        return;
                    }
                    try {
                        int value = parseOffsetInput(text, scale);
                        int clamped = Math.max(min, Math.min(max, value));
                        consumer.accept(clamped);
                    } catch (NumberFormatException ignored) {
                        showToast(scale == 100 ? "请输入 0.01dp 精度的数值" : "请输入 0.1dp 精度的数值");
                    }
                })
                .show();
        styleDialog(dialog);
        attachDialogButtonHaptics(dialog);
    }

    void showTextInputDialog(String titleText, String currentValue, String message,
            String inputHint, boolean plainTextInput, TextValueConsumer consumer) {
        showTextInputDialog(titleText, currentValue, message, inputHint, plainTextInput,
                "清空", "", consumer);
    }

    void showTextInputDialog(String titleText, String currentValue, String message,
            String inputHint, boolean plainTextInput, String neutralButtonText,
            String neutralValue, TextValueConsumer consumer) {
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
                .setNeutralButton(neutralButtonText,
                        (dialogInterface, which) -> consumer.accept(neutralValue))
                .setNegativeButton("\u53d6\u6d88", null)
                .setPositiveButton("\u786e\u5b9a", (dialogInterface, which) ->
                        consumer.accept(input.getText() == null ? "" : input.getText().toString().trim()))
                .show();
        styleDialog(dialog);
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
        return parseOffsetInput(text, 10);
    }

    int parseOffsetInput(String text, int scale) {
        String normalized = text == null ? "" : text.trim().replace(',', '.');
        if (normalized.length() == 0) {
            throw new NumberFormatException("empty");
        }
        BigDecimal value = new BigDecimal(normalized);
        BigDecimal scaled = value.multiply(BigDecimal.valueOf(scale)).setScale(0, RoundingMode.HALF_UP);
        return scaled.intValueExact();
    }

    private String formatSliderDisplayValue(int value, String suffix, boolean insetValue) {
        return insetValue ? formatInsetValue(value) : formatValue(value, suffix);
    }

    public void setTapClickListener(View view, View.OnClickListener listener) {
        if (view == null || listener == null) {
            return;
        }
        view.setHapticFeedbackEnabled(true);
        view.setFocusable(true);
        view.setClipToOutline(true);
        if (!(view.getForeground() instanceof RippleDrawable)) {
            view.setForeground(new RippleDrawable(
                    ColorStateList.valueOf(0x246750A4), null, roundRect(Color.WHITE, 0)));
        }
        view.setOnClickListener(v -> {
            performTapHaptic(v);
            listener.onClick(v);
        });
    }

    public void styleSwitch(Switch toggle) {
        if (toggle == null) {
            return;
        }
        toggle.setShowText(false);
        toggle.setSplitTrack(false);
        toggle.setSwitchMinWidth(dp(52));
        toggle.setMinWidth(dp(52));
        toggle.setMinHeight(dp(48));
        toggle.setTrackTintList(null);
        toggle.setThumbTintList(null);
        toggle.setTrackDrawable(buildSwitchTrackDrawable());
        toggle.setThumbDrawable(buildSwitchThumbDrawable());
    }

    public void styleSeekBar(SeekBar seekBar) {
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

    public void styleDialog(AlertDialog dialog) {
        if (dialog == null) {
            return;
        }
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(roundRect(colorSurfaceSoft, 28));
        }
        TextView positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        TextView negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        TextView neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        if (positive != null) {
            positive.setTextColor(Color.WHITE);
            positive.setBackground(roundRect(colorPrimary, 16));
            positive.setMinHeight(dp(48));
        }
        if (negative != null) {
            negative.setTextColor(colorPrimary);
        }
        if (neutral != null) {
            neutral.setTextColor(colorPrimary);
        }
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

    public void performTapHaptic(View view) {
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

    public void attachDialogButtonHaptics(AlertDialog dialog) {
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

    public void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    public void bindRestartCardContent(LinearLayout content) {
        restartCardContent = content;
        HomePageController.renderRestartTargets(this, content, null, true);
    }

    public void refreshRestartScope() {
        if (!scopeRefreshActive || restartCardContent == null) {
            return;
        }
        int generation = ++scopeRefreshGeneration;
        HomePageController.renderRestartTargets(this, restartCardContent, null, true);
        new Thread(() -> {
            Set<String> scope = RemoteSettingsSync.readEnabledScope();
            scopeHandler.post(() -> {
                if (!scopeRefreshActive || generation != scopeRefreshGeneration) {
                    return;
                }
                HomePageController.renderRestartTargets(this, restartCardContent, scope, false);
                if (refreshSearchResults != null) refreshSearchResults.run();
            });
        }, "module-scope-reader").start();
    }

    public boolean isRestartTargetInstalled(RestartTarget target) {
        if (target == RestartTarget.FRAMEWORK) {
            return true;
        }
        try {
            getPackageManager().getPackageInfo(target.packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public void restartScopeApp(RestartTarget target) {
        if (batchRestartRunning) {
            showToast("正在批量重启，请稍候");
            return;
        }
        if (!isRestartTargetInstalled(target)) {
            showToast(target.label + "未安装");
            return;
        }
        switch (target) {
            case SYSTEM_UI -> restartSystemUi();
            case FRAMEWORK -> confirmDeviceRestart();
            default -> restartRootCommands(target.label, target.restartCommands(), target.stopOnFirstSuccess());
        }
    }

    public boolean isBatchRestartRunning() {
        return batchRestartRunning;
    }

    public List<RestartTarget> getBatchRestartTargets(Set<String> enabledScope) {
        List<RestartTarget> targets = new ArrayList<>();
        if (enabledScope == null) return targets;
        for (RestartTarget target : RestartTarget.values()) {
            if (target != RestartTarget.FRAMEWORK && enabledScope.contains(target.packageName)
                    && isRestartTargetInstalled(target)) {
                targets.add(target);
            }
        }
        // Restart UI hosts after the services and apps they may reconnect to.
        if (targets.remove(RestartTarget.LAUNCHER)) targets.add(RestartTarget.LAUNCHER);
        if (targets.remove(RestartTarget.SYSTEM_UI)) targets.add(RestartTarget.SYSTEM_UI);
        return targets;
    }

    public void restartAllScopeApps(Set<String> enabledScope) {
        if (batchRestartRunning) {
            showToast("正在批量重启，请稍候");
            return;
        }
        List<RestartTarget> targets = getBatchRestartTargets(enabledScope);
        if (targets.isEmpty()) {
            showToast("没有可批量重启的应用");
            return;
        }
        List<String> labels = new ArrayList<>();
        for (RestartTarget target : targets) labels.add(target.label);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("重启全部应用")
                .setMessage("将依次重启以下 " + targets.size() + " 个应用：\n\n"
                        + TextUtils.join("、", labels)
                        + "\n\n不包含系统框架，不会重启手机。请先保存正在进行的操作。")
                .setNegativeButton("取消", null)
                .setPositiveButton("重启全部", (d, which) -> startRestartBatch(targets, enabledScope))
                .create();
        dialog.show();
        attachDialogButtonHaptics(dialog);
    }

    void startRestartBatch(List<RestartTarget> targets, Set<String> enabledScope) {
        if (batchRestartRunning) return;
        batchRestartRunning = true;
        if (restartCardContent != null) {
            HomePageController.renderRestartTargets(this, restartCardContent, enabledScope, false);
        }
        showToast("正在批量重启 " + targets.size() + " 个应用…");
        Runnable start = () -> new Thread(() -> {
            String result = runRestartBatch(targets);
            scopeHandler.post(() -> {
                batchRestartRunning = false;
                Toast.makeText(this, result, Toast.LENGTH_LONG).show();
                refreshRestartScope();
            });
        }, "scope-app-restarter").start();
        if (targets.contains(RestartTarget.SYSTEM_UI)) {
            returnHomeForRestart();
            scopeHandler.postDelayed(start, SYSTEM_UI_RESTART_DELAY_MS);
        } else {
            start.run();
        }
    }

    String runRestartBatch(List<RestartTarget> targets) {
        int total = 0;
        int success = 0;
        List<String> failed = new ArrayList<>();
        for (RestartTarget target : targets) {
            if (target == RestartTarget.FRAMEWORK) continue;
            total++;
            if (isRestartTargetInstalled(target)
                    && executeRootCommands(target.restartCommands(), target.stopOnFirstSuccess()).success()) {
                success++;
            } else {
                failed.add(target.label);
            }
        }
        return "批量重启完成：成功 " + success + "/" + total + " 个"
                + (failed.isEmpty() ? "" : "；失败：" + TextUtils.join("、", failed) + "。请检查 Root 权限。");
    }

    private void confirmDeviceRestart() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("重启手机")
                .setMessage("系统框架需要重启整部手机才能重新加载模块。请先保存正在进行的操作，是否立即重启？")
                .setNegativeButton("取消", null)
                .setPositiveButton("重启手机", (d, which) ->
                        restartRootCommands("手机", new String[]{"reboot"}))
                .create();
        dialog.show();
        attachDialogButtonHaptics(dialog);
    }

    public void restartSystemUi() {
        returnHomeForRestart();
        scopeHandler.postDelayed(() -> restartRootCommands("SystemUI", RestartTarget.SYSTEM_UI.restartCommands()),
                SYSTEM_UI_RESTART_DELAY_MS);
    }

    private void returnHomeForRestart() {
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(homeIntent);
        moveTaskToBack(true);
    }

    public void detectOneMindHookPoints(TextView statusView) {
        if (statusView != null) {
            statusView.setText("检测中...");
        }
        new Thread(() -> {
            String result = OneMindHookPointDetector.detect(this);
            new Handler(Looper.getMainLooper()).post(() -> {
                if (statusView != null) {
                    statusView.setText(result);
                }
                showToast(result);
            });
        }).start();
    }

    private void restartRootCommands(String label, String[] commands) {
        restartRootCommands(label, commands, true);
    }

    void restartRootCommands(String label, String[] commands, boolean stopOnFirstSuccess) {
        showToast("\u6b63\u5728\u91cd\u542f" + label + "...");
        new Thread(() -> {
            RootRestartResult result = executeRootCommands(commands, stopOnFirstSuccess);
            boolean finalSuccess = result.success();
            String finalError = result.error();
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

    record RootRestartResult(boolean success, String error) {}

    RootRestartResult executeRootCommands(String[] commands, boolean stopOnFirstSuccess) {
        boolean success = false;
        String error = null;
        try {
            for (String command : commands) {
                Process process = new ProcessBuilder("su", "-c", command)
                        .redirectErrorStream(true).start();
                String output = readText(process.getInputStream()).trim();
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    success = true;
                    if (stopOnFirstSuccess) break;
                }
                if (!output.isEmpty()) error = output;
            }
        } catch (Throwable t) {
            error = t.getMessage();
        }
        return new RootRestartResult(success, error);
    }

    public LinearLayout card(int color, int radiusDp) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        card.setBackground(roundRect(color, Math.min(radiusDp, 24)));
        return card;
    }

    public TextView chip(String text, int backgroundColor, int textColor) {
        return settingsUiFactory.chip(text, backgroundColor, textColor);
    }

    public TextView filledButton(String text, int backgroundColor, int textColor) {
        return settingsUiFactory.filledButton(text, backgroundColor, textColor);
    }

    int backgroundColor() {
        return colorBackground;
    }

    public int surfaceColor() {
        return colorSurface;
    }

    public int surfaceSoftColor() {
        return colorSurfaceSoft;
    }

    public int surfaceStrongColor() {
        return colorSurfaceStrong;
    }

    public int featureSurfaceColor() {
        return colorFeatureSurface;
    }

    public int featureStrokeColor() {
        return colorFeatureStroke;
    }

    public int textColor() {
        return colorText;
    }

    public int subtextColor() {
        return colorSubtext;
    }

    public int primaryColor() {
        return colorPrimary;
    }

    public int primaryContainerColor() {
        return colorPrimaryContainer;
    }

    int primaryDeepColor() {
        return colorPrimaryDeep;
    }

    int secondaryColor() {
        return 0xFF625B71;
    }

    public int tertiaryColor() {
        return 0xFF7D5260;
    }

    int errorColor() {
        return 0xFFB3261E;
    }

    public int strokeColor() {
        return colorStroke;
    }

    public String githubUrl() {
        return GITHUB_URL;
    }

    public String qqGroupUrl() {
        return QQ_GROUP_URL;
    }

    public String qqGroupNumber() {
        return QQ_GROUP_NUMBER;
    }

    public String supportedScopesSummary() {
        return "android / com.android.systemui / 主流输入法";
    }

    public SharedPreferences prefs() {
        return prefs;
    }

    public ClockExpressionEditor clockExpressionEditor() {
        return clockExpressionEditor;
    }

    public ImeToolbarEditor imeToolbarEditor() {
        return imeToolbarEditor;
    }

    public ArrayList<PositionOffsetSliderBinding> positionTuningSliderBindings() {
        return positionTuningSliderBindings;
    }

    public void openExternalLink(String url) {
        if (TextUtils.isEmpty(url)) {
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Throwable t) {
            showToast("无法打开链接：" + url);
        }
    }

    public View createIconSizingCard() {
        return settingsCardFactory.createIconSizingCard();
    }

    public View createBatterySettingsCard() {
        return settingsCardFactory.createBatterySettingsCard();
    }

    public View createNotificationSettingsCard() {
        return settingsCardFactory.createNotificationSettingsCard();
    }

    public View createSignalSettingsCard() {
        return settingsCardFactory.createSignalSettingsCard();
    }

    public View createConnectionRateSettingsCard() {
        return settingsCardFactory.createConnectionRateSettingsCard();
    }

    public View createTimeExpressionSettingsCard() {
        return settingsCardFactory.createTimeExpressionSettingsCard();
    }

    public View createTimeInteractionSettingsCard() {
        return settingsCardFactory.createTimeInteractionSettingsCard();
    }

    public View createTimeTypographySettingsCard() {
        return settingsCardFactory.createTimeTypographySettingsCard();
    }

    public View createMBackActionSettingsCard() {
        return settingsCardFactory.createMBackActionSettingsCard();
    }

    public View createWindowModeSideGestureSettingsCard() {
        return settingsCardFactory.createWindowModeSideGestureSettingsCard();
    }

    public View createAssistantGestureSettingsCard() {
        return settingsCardFactory.createAssistantGestureSettingsCard();
    }

    public View createShareTargetsSettingsCard() {
        return settingsCardFactory.createShareTargetsSettingsCard();
    }

    public View createCarLinkSettingsCard() {
        return settingsCardFactory.createCarLinkSettingsCard();
    }

    public View createMBackNavigationSettingsCard() {
        return settingsCardFactory.createMBackNavigationSettingsCard();
    }

    public View createImeToolbarSettingsCard() {
        return settingsCardFactory.createImeToolbarSettingsCard();
    }

    public View createLauncherRecentsSettingsCard() {
        return settingsCardFactory.createLauncherRecentsSettingsCard();
    }

    public View createSystemAppearanceSettingsCard() {
        return settingsCardFactory.createSystemAppearanceSettingsCard();
    }

    public View createAdvancedToolsCard() {
        return settingsCardFactory.createAdvancedToolsCard();
    }

    public View createConfigManagementCard() {
        return settingsCardFactory.createConfigManagementCard();
    }

    public View createPerformanceDebugCard() {
        return settingsCardFactory.createPerformanceDebugCard();
    }

    public View createOneMindPerfControlCard() {
        return settingsCardFactory.createOneMindPerfControlCard();
    }

    public View createMzSafeOptimizationCard() {
        return settingsCardFactory.createMzSafeOptimizationCard();
    }

    public View createPositionTuningSettingsCard() {
        return settingsCardFactory.createPositionTuningSettingsCard();
    }

    View createCameraCirclePositionCard() {
        return settingsCardFactory.createCameraCirclePositionCard();
    }

    public View createLauncherStackParamsSettingsCard() {
        return settingsCardFactory.createLauncherStackParamsSettingsCard();
    }

    public View createTelephonyDebugSettingsCard() {
        return settingsCardFactory.createTelephonyDebugSettingsCard();
    }

    public View buildSectionCard(String titleText, String subtitleText, View content) {
        return buildSectionCard(titleText, subtitleText, content, false);
    }

    public View buildSectionCard(String titleText, String subtitleText, View content, boolean expanded) {
        LinearLayout card = card(colorSurface, 24);
        card.setPadding(0, 0, 0, 0);
        card.setClipToOutline(true);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(12), dp(16), dp(12));
        header.setMinimumHeight(dp(64));

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(colorText);
        title.setTextSize(18);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        header.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        addHelpButton(header, titleText, subtitleText);
        card.addView(header, matchWrap());

        if (content != null) {
            ImageView arrow = new ImageView(this);
            arrow.setImageResource(R.drawable.ic_section_expand);
            arrow.setRotation(expanded ? 0f : -90f);
            arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            header.addView(arrow, new LinearLayout.LayoutParams(dp(24), dp(24)));
            content.setVisibility(expanded ? View.VISIBLE : View.GONE);
            LinearLayout.LayoutParams contentLp = matchWrapWithTop(4);
            contentLp.leftMargin = dp(16);
            contentLp.rightMargin = dp(16);
            contentLp.bottomMargin = dp(12);
            card.addView(content, contentLp);
            header.setPadding(dp(16), dp(12), dp(16), expanded ? 0 : dp(12));
            header.setMinimumHeight(dp(expanded ? 52 : 64));
            header.setContentDescription(titleText + (expanded
                    ? "，已展开，点击收起" : "，已收起，点击展开"));
            setTapClickListener(header, v -> {
                boolean expand = content.getVisibility() != View.VISIBLE;
                View focused = content.findFocus();
                if (!expand && focused != null) {
                    focused.clearFocus();
                    getSystemService(InputMethodManager.class)
                            .hideSoftInputFromWindow(content.getWindowToken(), 0);
                }
                content.setVisibility(expand ? View.VISIBLE : View.GONE);
                header.setPadding(dp(16), dp(12), dp(16), expand ? 0 : dp(12));
                header.setMinimumHeight(dp(expand ? 52 : 64));
                arrow.setRotation(expand ? 0f : -90f);
                header.setContentDescription(titleText + (expand
                        ? "，已展开，点击收起" : "，已收起，点击展开"));
            });
            Runnable expandSection = () -> {
                if (content.getVisibility() != View.VISIBLE) header.performClick();
            };
            header.setTag(R.id.feature_search_expand, expandSection);
            content.setTag(R.id.feature_search_expand, expandSection);
            content.setTag(R.id.feature_search_category, titleText);
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
        colorBackground = getColor(R.color.flyme_background);
        colorSurface = getColor(R.color.flyme_surface);
        colorSurfaceSoft = getColor(R.color.flyme_surface_low);
        colorSurfaceStrong = getColor(R.color.flyme_surface_container_highest);
        colorFeatureSurface = getColor(R.color.flyme_primary_container);
        colorFeatureStroke = getColor(R.color.flyme_primary);
        colorText = getColor(R.color.flyme_text);
        colorSubtext = getColor(R.color.flyme_subtext);
        colorPrimary = getColor(R.color.flyme_primary);
        colorPrimaryContainer = getColor(R.color.flyme_primary_container);
        colorPrimaryDeep = getColor(R.color.flyme_primary_deep);
        colorStroke = getColor(R.color.flyme_outline_variant);
    }

    private void configureSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            getWindow().setStatusBarColor(colorBackground);
            getWindow().setNavigationBarColor(colorBackground);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = getWindow().getDecorView().getSystemUiVisibility();
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    public GradientDrawable roundRect(int color, int radiusDp) {
        return settingsUiFactory.roundRect(color, radiusDp);
    }

    public GradientDrawable outlinedRect(int color, int strokeColor, int strokeWidthDp, int radiusDp) {
        return settingsUiFactory.outlinedRect(color, strokeColor, strokeWidthDp, radiusDp);
    }

    public LinearLayout.LayoutParams matchWrap() {
        return settingsUiFactory.matchWrap();
    }

    public LinearLayout.LayoutParams matchWrapWithTop(int topDp) {
        return settingsUiFactory.matchWrapWithTop(topDp);
    }

    public int dp(int value) {
        return settingsUiFactory.dp(value);
    }

    private interface IntValueConsumer {
        void accept(int value);
    }

}
