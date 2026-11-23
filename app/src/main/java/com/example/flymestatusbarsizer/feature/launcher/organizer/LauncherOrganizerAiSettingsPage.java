package com.example.flymestatusbarsizer.feature.launcher.organizer;

import com.example.flymestatusbarsizer.MainActivity;
import com.example.flymestatusbarsizer.ui.PageViewUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class LauncherOrganizerAiSettingsPage {
    static final class EndpointOption {
        final String label;
        final String path;
        final boolean isResponses;

        EndpointOption(String label, String path, boolean isResponses) {
            this.label = label;
            this.path = path;
            this.isResponses = isResponses;
        }
    }

    static final EndpointOption[] ENDPOINT_OPTIONS = {
            new EndpointOption("OpenAI Chat Completions (/v1/chat/completions)", "/v1/chat/completions", false),
            new EndpointOption("OpenAI Responses (/v1/responses)", "/v1/responses", true),
            new EndpointOption("Chat Completions (/chat/completions)", "/chat/completions", false),
            new EndpointOption("自定义路径...", "", false)
    };

    private final MainActivity activity;
    private final SharedPreferences prefs;
    private final EditText baseUrlInput;
    private final TextView endpointTypeButton;
    private final LinearLayout customPathContainer;
    private final EditText customPathInput;
    private final TextView combinedUrlPreview;
    private final EditText modelInput;
    private final EditText keyInput;
    private final EditText corePromptInput;
    private final TextView testResult;
    private final TextView testButton;

    private int selectedEndpointOptionIndex = 0;

    public static void bind(MainActivity activity, LinearLayout root) {
        new LauncherOrganizerAiSettingsPage(activity, root);
    }

    private LauncherOrganizerAiSettingsPage(MainActivity activity, LinearLayout root) {
        this.activity = activity;
        this.prefs = activity.getSharedPreferences("launcher_organizer_ai", Context.MODE_PRIVATE);

        // 1. AI 接口与模型配置
        LinearLayout apiSection = column();

        String rawEndpoint = prefs.getString("endpoint", "");
        EndpointParts parts = splitEndpointUrl(rawEndpoint);
        selectedEndpointOptionIndex = parts.optionIndex;

        baseUrlInput = input(apiSection, "API 基础网址（Base URL）",
                "例如 https://api.openai.com 或中转代理地址", parts.baseUrl, false);

        label(apiSection, "接口路径 / 端点类型", 14);
        endpointTypeButton = actionButton(ENDPOINT_OPTIONS[selectedEndpointOptionIndex].label + " ▾");
        activity.setTapClickListener(endpointTypeButton, v -> chooseEndpointOption());
        apiSection.addView(endpointTypeButton, activity.matchWrapWithTop(4));

        customPathContainer = column();
        customPathInput = input(customPathContainer, "自定义接口路径",
                "例如 /v1/chat/completions", parts.path, false);
        boolean isCustom = (selectedEndpointOptionIndex == ENDPOINT_OPTIONS.length - 1);
        customPathContainer.setVisibility(isCustom ? View.VISIBLE : View.GONE);
        apiSection.addView(customPathContainer, PageViewUtils.matchWrap());

        combinedUrlPreview = new TextView(activity);
        combinedUrlPreview.setTextSize(12);
        combinedUrlPreview.setTextColor(activity.subtextColor());
        apiSection.addView(combinedUrlPreview, activity.matchWrapWithTop(4));
        updateCombinedUrlPreview();

        modelInput = input(apiSection, "模型名称",
                "填写服务商提供的模型名称", prefs.getString("model", ""), false);
        keyInput = input(apiSection, "API Key",
                "仅保存在本机模块内", prefs.getString("key", ""), true);

        testButton = actionButton("测试接口");
        activity.setTapClickListener(testButton, v -> testAiConnection());
        apiSection.addView(testButton, activity.matchWrapWithTop(12));

        testResult = new TextView(activity);
        testResult.setTextSize(13);
        testResult.setVisibility(View.GONE);
        apiSection.addView(testResult, activity.matchWrapWithTop(8));

        root.addView(activity.buildSectionCard("AI 接口配置", "配置兼容 OpenAI 格式的大语言模型服务及密钥", apiSection, true),
                activity.matchWrapWithTop(8));

        // 2. 核心提示词（低频项，默认折叠）
        LinearLayout promptSection = column();
        LinearLayout coreContainer = column();
        coreContainer.setVisibility(View.GONE);

        TextView toggleCore = activity.filledButton("核心提示词（默认折叠，点击展开）",
                activity.surfaceStrongColor(), activity.textColor());
        if (toggleCore instanceof MaterialButton mb) {
            mb.setStrokeColor(ColorStateList.valueOf(activity.strokeColor()));
            mb.setStrokeWidth(activity.dp(1));
            mb.setCornerRadius(activity.dp(10));
        }
        toggleCore.setTextSize(13);
        toggleCore.setMinHeight(activity.dp(38));
        toggleCore.setPadding(activity.dp(12), 0, activity.dp(12), 0);
        promptSection.addView(toggleCore, PageViewUtils.matchWrap());

        corePromptInput = promptInput(coreContainer, "核心提示词",
                "分类规则和返回格式",
                prefs.getString("core_prompt", LauncherOrganizerPage.DEFAULT_CORE_PROMPT), 6, 12);

        TextView resetPrompt = actionButton("恢复默认核心提示词");
        activity.setTapClickListener(resetPrompt, v -> {
            corePromptInput.setText(LauncherOrganizerPage.DEFAULT_CORE_PROMPT);
            saveSettings(false);
            Toast.makeText(activity, "已恢复默认核心提示词", Toast.LENGTH_SHORT).show();
        });
        coreContainer.addView(resetPrompt, activity.matchWrapWithTop(8));

        activity.setTapClickListener(toggleCore, v -> {
            boolean show = coreContainer.getVisibility() != View.VISIBLE;
            coreContainer.setVisibility(show ? View.VISIBLE : View.GONE);
            toggleCore.setText(show ? "核心提示词（点击收起）" : "核心提示词（默认折叠，点击展开）");
        });
        promptSection.addView(coreContainer, PageViewUtils.matchWrap());

        root.addView(activity.buildSectionCard("核心提示词", "AI 分类规则和数据返回格式约束，通常无需修改", promptSection, true),
                activity.matchWrapWithTop(12));

        // 3. 保存配置按钮
        TextView saveButton = activity.filledButton("保存配置", activity.primaryColor(), Color.WHITE);
        saveButton.setMinHeight(activity.dp(48));
        saveButton.setTextSize(15);
        activity.setTapClickListener(saveButton, v -> {
            saveSettings(true);
            Toast.makeText(activity, "AI 配置已保存", Toast.LENGTH_SHORT).show();
        });
        root.addView(saveButton, activity.matchWrapWithTop(16));

        // 自动监听输入保存
        TextWatcher autoSaveWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { saveSettings(false); }
        };
        TextWatcher urlWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                updateCombinedUrlPreview();
                saveSettings(false);
            }
        };
        baseUrlInput.addTextChangedListener(urlWatcher);
        customPathInput.addTextChangedListener(urlWatcher);
        modelInput.addTextChangedListener(autoSaveWatcher);
        keyInput.addTextChangedListener(autoSaveWatcher);
        corePromptInput.addTextChangedListener(autoSaveWatcher);
    }

    private void chooseEndpointOption() {
        String[] titles = new String[ENDPOINT_OPTIONS.length];
        for (int i = 0; i < titles.length; i++) {
            titles[i] = ENDPOINT_OPTIONS[i].label;
        }
        new MaterialAlertDialogBuilder(activity)
                .setTitle("选择接口类型与路径")
                .setSingleChoiceItems(titles, selectedEndpointOptionIndex, (dialog, which) -> {
                    selectedEndpointOptionIndex = which;
                    dialog.dismiss();
                    updateEndpointTypeViews();
                    updateCombinedUrlPreview();
                    saveSettings(false);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateEndpointTypeViews() {
        if (selectedEndpointOptionIndex >= 0 && selectedEndpointOptionIndex < ENDPOINT_OPTIONS.length) {
            endpointTypeButton.setText(ENDPOINT_OPTIONS[selectedEndpointOptionIndex].label + " ▾");
            boolean isCustom = (selectedEndpointOptionIndex == ENDPOINT_OPTIONS.length - 1);
            customPathContainer.setVisibility(isCustom ? View.VISIBLE : View.GONE);
        }
    }

    private String getEffectiveEndpointUrl() {
        String base = baseUrlInput.getText().toString().trim();
        String path;
        if (selectedEndpointOptionIndex >= 0 && selectedEndpointOptionIndex < ENDPOINT_OPTIONS.length - 1) {
            path = ENDPOINT_OPTIONS[selectedEndpointOptionIndex].path;
        } else {
            path = customPathInput.getText().toString().trim();
        }
        return buildEndpointUrl(base, path);
    }

    private void updateCombinedUrlPreview() {
        String effective = getEffectiveEndpointUrl();
        if (effective.isEmpty()) {
            combinedUrlPreview.setText("完整接口地址：未填写基础网址");
        } else {
            combinedUrlPreview.setText("完整接口地址：" + effective);
        }
    }

    private void saveSettings(boolean notify) {
        String coreText = corePromptInput.getText().toString().trim();
        if (coreText.isEmpty()) coreText = LauncherOrganizerPage.DEFAULT_CORE_PROMPT;
        String combined = getEffectiveEndpointUrl();
        prefs.edit()
                .putString("endpoint", combined)
                .putString("model", modelInput.getText().toString().trim())
                .putString("key", keyInput.getText().toString().trim())
                .putString("core_prompt", coreText)
                .apply();
    }

    private void testAiConnection() {
        String address = getEffectiveEndpointUrl();
        String modelName = modelInput.getText().toString().trim();
        String apiKey = keyInput.getText().toString().trim();
        if (address.isEmpty() || modelName.isEmpty()) {
            testResult.setVisibility(View.VISIBLE);
            testResult.setTextColor(activity.errorColor());
            testResult.setText("请先填写基础网址和模型名称");
            return;
        }

        testButton.setEnabled(false);
        testButton.setText("测试中...");
        testResult.setVisibility(View.VISIBLE);
        testResult.setTextColor(activity.subtextColor());
        testResult.setText("正在测试连接...");

        new Thread(() -> {
            String resultText;
            boolean success = false;
            try {
                URL url = new URL(address);
                if (!"https".equalsIgnoreCase(url.getProtocol()) || url.getHost().isEmpty()
                        || url.getUserInfo() != null) {
                    throw new IllegalArgumentException("接口地址必须是完整 HTTPS 地址");
                }
                boolean isResponses = isResponsesApi(address);
                JSONObject request;
                if (isResponses) {
                    request = new JSONObject().put("model", modelName).put("stream", false)
                            .put("max_output_tokens", 1)
                            .put("input", new JSONArray().put(new JSONObject()
                                    .put("role", "user").put("content", "回复 1")));
                } else {
                    request = new JSONObject().put("model", modelName).put("stream", false)
                            .put("max_tokens", 1)
                            .put("messages", new JSONArray().put(new JSONObject()
                                    .put("role", "user").put("content", "回复 1")));
                }
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                try {
                    connection.setRequestMethod("POST");
                    connection.setConnectTimeout(10_000);
                    connection.setReadTimeout(20_000);
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    if (!apiKey.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + apiKey);
                    connection.setDoOutput(true);
                    byte[] bytes = request.toString().getBytes(StandardCharsets.UTF_8);
                    connection.setFixedLengthStreamingMode(bytes.length);
                    try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
                    int code = connection.getResponseCode();
                    if (code < 200 || code >= 300) {
                        throw new IllegalStateException("接口返回 HTTP " + code + "，请核对地址、模型和 API Key");
                    }
                    resultText = "接口连接成功，可以开始生成分类";
                    success = true;
                } finally {
                    connection.disconnect();
                }
            } catch (Exception error) {
                resultText = error.getMessage() == null ? "接口连接失败" : error.getMessage();
                if (!apiKey.isEmpty()) resultText = resultText.replace(apiKey, "••••");
            }
            final String finalMsg = resultText;
            final boolean finalSuccess = success;
            activity.runOnUiThread(() -> {
                testButton.setEnabled(true);
                testButton.setText("测试接口");
                testResult.setVisibility(View.VISIBLE);
                testResult.setTextColor(finalSuccess ? activity.primaryColor() : activity.errorColor());
                testResult.setText(finalMsg);
            });
        }, "FlymeAiConnectionTest").start();
    }

    public static String buildEndpointUrl(String baseUrl, String path) {
        if (baseUrl == null) baseUrl = "";
        baseUrl = baseUrl.trim();
        if (baseUrl.isEmpty()) return "";
        if (path == null) path = "";
        path = path.trim();
        if (path.isEmpty()) return baseUrl;

        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        String lowerBase = baseUrl.toLowerCase(java.util.Locale.ROOT);
        String lowerPath = path.toLowerCase(java.util.Locale.ROOT);
        if (lowerBase.endsWith("/v1") && (lowerPath.startsWith("/v1/") || lowerPath.equals("/v1"))) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 3);
            while (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
        }

        return baseUrl + path;
    }

    public static class EndpointParts {
        public final String baseUrl;
        public final String path;
        public final int optionIndex;

        public EndpointParts(String baseUrl, String path, int optionIndex) {
            this.baseUrl = baseUrl;
            this.path = path;
            this.optionIndex = optionIndex;
        }
    }

    public static EndpointParts splitEndpointUrl(String endpoint) {
        if (endpoint == null || endpoint.trim().isEmpty()) {
            return new EndpointParts("", ENDPOINT_OPTIONS[0].path, 0);
        }
        endpoint = endpoint.trim();
        while (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }

        String lower = endpoint.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith("/v1/chat/completions")) {
            String base = endpoint.substring(0, endpoint.length() - "/v1/chat/completions".length());
            return new EndpointParts(trimTrailingSlash(base), "/v1/chat/completions", 0);
        }
        if (lower.endsWith("/v1/responses")) {
            String base = endpoint.substring(0, endpoint.length() - "/v1/responses".length());
            return new EndpointParts(trimTrailingSlash(base), "/v1/responses", 1);
        }
        if (lower.endsWith("/chat/completions")) {
            String base = endpoint.substring(0, endpoint.length() - "/chat/completions".length());
            return new EndpointParts(trimTrailingSlash(base), "/chat/completions", 2);
        }
        if (lower.endsWith("/responses")) {
            String base = endpoint.substring(0, endpoint.length() - "/responses".length());
            return new EndpointParts(trimTrailingSlash(base), "/responses", 1);
        }

        try {
            URL url = new URL(endpoint);
            String path = url.getPath();
            if (path == null || path.isEmpty() || "/".equals(path) || "/v1".equals(path)) {
                return new EndpointParts(endpoint, ENDPOINT_OPTIONS[0].path, 0);
            }
            String base = url.getProtocol() + "://" + url.getHost() + (url.getPort() != -1 ? ":" + url.getPort() : "");
            return new EndpointParts(base, path, 3);
        } catch (Exception ignored) {
            return new EndpointParts(endpoint, ENDPOINT_OPTIONS[0].path, 0);
        }
    }

    private static String trimTrailingSlash(String str) {
        while (str.endsWith("/")) {
            str = str.substring(0, str.length() - 1);
        }
        return str;
    }

    public static boolean isResponsesApi(String address) {
        if (address == null) return false;
        String lower = address.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith("/responses") || lower.endsWith("/responses/") || lower.contains("/responses?");
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView label(LinearLayout root, String text, int size) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(activity.textColor());
        root.addView(view, activity.matchWrapWithTop(8));
        return view;
    }

    private EditText input(LinearLayout root, String title, String hint, String value, boolean password) {
        label(root, title, 14);
        EditText edit = new EditText(activity);
        edit.setTextColor(activity.textColor());
        edit.setHintTextColor(activity.subtextColor());
        edit.setSingleLine(true);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | (password ? InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_TEXT_VARIATION_URI));
        edit.setHint(hint);
        edit.setText(value);
        edit.setTextSize(14);
        edit.setBackground(activity.roundRect(activity.surfaceStrongColor(), 8));
        edit.setPadding(activity.dp(12), activity.dp(8), activity.dp(12), activity.dp(8));
        edit.setSaveEnabled(false);
        edit.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        root.addView(edit, activity.matchWrapWithTop(4));
        return edit;
    }

    private EditText promptInput(LinearLayout root, String title, String hint, String value, int minLines, int maxLines) {
        label(root, title, 14);
        EditText edit = new EditText(activity);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        edit.setSingleLine(false);
        edit.setMinLines(minLines);
        edit.setMaxLines(maxLines);
        edit.setTextSize(14);
        edit.setTextColor(activity.textColor());
        edit.setHintTextColor(activity.subtextColor());
        edit.setHint(hint);
        edit.setText(value);
        edit.setLineSpacing(activity.dp(3), 1f);
        edit.setGravity(Gravity.TOP | Gravity.START);
        edit.setPadding(activity.dp(12), activity.dp(10), activity.dp(12), activity.dp(10));
        edit.setBackground(activity.roundRect(activity.surfaceStrongColor(), 8));
        edit.setVerticalScrollBarEnabled(true);
        float[] lastY = {0f};
        edit.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN -> {
                    lastY[0] = event.getY();
                    view.getParent().requestDisallowInterceptTouchEvent(
                            view.canScrollVertically(-1) || view.canScrollVertically(1));
                }
                case MotionEvent.ACTION_MOVE -> {
                    float delta = lastY[0] - event.getY();
                    if (delta != 0f) {
                        view.getParent().requestDisallowInterceptTouchEvent(
                                view.canScrollVertically(delta > 0f ? 1 : -1));
                    }
                    lastY[0] = event.getY();
                }
                case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        view.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return false;
        });
        root.addView(edit, activity.matchWrapWithTop(4));
        return edit;
    }

    private TextView actionButton(String title) {
        TextView button = activity.filledButton(title, activity.surfaceStrongColor(), activity.primaryColor());
        if (button instanceof MaterialButton mb) {
            mb.setStrokeColor(ColorStateList.valueOf(activity.strokeColor()));
            mb.setStrokeWidth(activity.dp(1));
            mb.setCornerRadius(activity.dp(10));
            mb.setInsetTop(0);
            mb.setInsetBottom(0);
        }
        button.setTextSize(13);
        button.setMinHeight(activity.dp(36));
        button.setPadding(activity.dp(12), 0, activity.dp(12), 0);
        return button;
    }
}
