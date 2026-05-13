package com.example.flymestatusbarsizer;

import android.content.ClipData;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.text.TextUtils;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.regex.Matcher;

final class ClockExpressionEditor {
    private final MainActivity activity;
    private final ArrayList<String> tokenOrder = new ArrayList<>();
    private final ArrayList<String> enabledTokens = new ArrayList<>();

    private LinearLayout orderContainer;
    private TextView previewView;

    ClockExpressionEditor(MainActivity activity) {
        this.activity = activity;
    }

    LinearLayout buildPage() {
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);

        activity.addProfileSectionHeader(page, "表达式编辑",
                "长按拖动表达式排序，点击表达式切换启用。小时、分钟、秒连续排列时会自动补冒号，不需要单独插入。");
        TextView hint = new TextView(activity);
        hint.setText("当前支持：小时、分钟、秒、星期、AM/PM、时段词、十二时辰地支和传统别称。");
        hint.setTextColor(activity.subtextColor());
        hint.setTextSize(13);
        hint.setPadding(0, activity.dp(10), 0, 0);
        page.addView(hint, activity.matchWrap());

        TextView orderTitle = new TextView(activity);
        orderTitle.setText("表达式列表");
        orderTitle.setTextColor(activity.primaryColor());
        orderTitle.setTextSize(15);
        orderTitle.setPadding(0, activity.dp(14), 0, 0);
        page.addView(orderTitle, activity.matchWrap());

        TextView orderHint = new TextView(activity);
        orderHint.setText("长按可拖动排序；单击即可启用或停用。启用项会按当前顺序生成下面的时间表达式。");
        orderHint.setTextColor(activity.subtextColor());
        orderHint.setTextSize(12);
        orderHint.setPadding(0, activity.dp(4), 0, 0);
        page.addView(orderHint, activity.matchWrap());

        previewView = new TextView(activity);
        previewView.setTextColor(activity.primaryColor());
        previewView.setTextSize(13);
        previewView.setPadding(activity.dp(12), activity.dp(10), activity.dp(12), activity.dp(10));
        previewView.setBackground(activity.roundRect(activity.surfaceSoftColor(), 18));
        page.addView(previewView, activity.matchWrapWithTop(10));

        orderContainer = new LinearLayout(activity);
        orderContainer.setOrientation(LinearLayout.VERTICAL);
        orderContainer.setPadding(0, activity.dp(12), 0, 0);
        page.addView(orderContainer, activity.matchWrap());
        loadDraft();
        renderEditor();

        activity.addDivider(page);
        activity.addActionButtonRow(page, "应用当前表达式",
                "保存当前启用状态和拖动顺序生成的表达式，并通知 SystemUI 立即刷新状态栏时间。",
                "应用", this::applyDraft);
        activity.addDivider(page);
        activity.addActionButtonRow(page, "清空当前表达式",
                "清空后只会关闭当前启用项，拖动顺序会保留。",
                "清空", this::clearDraft);
        return page;
    }

    private void loadDraft() {
        enabledTokens.clear();
        tokenOrder.clear();
        String raw = activity.readStringSetting(
                SettingsStore.KEY_CLOCK_CUSTOM_FORMAT,
                SettingsStore.DEFAULT_CLOCK_CUSTOM_FORMAT);
        if (!TextUtils.isEmpty(raw)) {
            Matcher matcher = MainActivity.CLOCK_EXPRESSION_TOKEN_PATTERN.matcher(raw);
            while (matcher.find()) {
                String token = matcher.group(1);
                if (isValidToken(token) && !enabledTokens.contains(token)) {
                    enabledTokens.add(token);
                }
            }
        }
        String storedOrder = activity.readStringSetting(
                SettingsStore.KEY_CLOCK_EXPRESSION_TOKEN_ORDER,
                SettingsStore.DEFAULT_CLOCK_EXPRESSION_TOKEN_ORDER);
        tokenOrder.addAll(parseStoredOrder(storedOrder));
        if (tokenOrder.isEmpty()) {
            tokenOrder.addAll(enabledTokens);
        }
        appendMissingTokens(tokenOrder);
    }

    private void renderEditor() {
        updatePreview();
        if (orderContainer == null) {
            return;
        }
        orderContainer.removeAllViews();
        if (tokenOrder.isEmpty()) {
            return;
        }
        for (int i = 0; i < tokenOrder.size(); i++) {
            String token = tokenOrder.get(i);
            orderContainer.addView(buildOrderRow(token), activity.matchWrap());
            if (i < tokenOrder.size() - 1) {
                View divider = new View(activity);
                divider.setBackgroundColor(activity.strokeColor());
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, activity.dp(1));
                lp.topMargin = activity.dp(8);
                lp.bottomMargin = activity.dp(8);
                orderContainer.addView(divider, lp);
            }
        }
    }

    private View buildOrderRow(String token) {
        boolean enabled = enabledTokens.contains(token);
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(activity.dp(14), activity.dp(12), activity.dp(14), activity.dp(12));
        row.setBackground(buildRowBackground(enabled, false));
        row.setTag(token);

        TextView drag = new TextView(activity);
        drag.setText("≡");
        drag.setTextColor(activity.primaryColor());
        drag.setTextSize(18);
        row.addView(drag, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout textColumn = new LinearLayout(activity);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.setPadding(activity.dp(12), 0, 0, 0);

        TextView title = new TextView(activity);
        title.setText(getTokenLabel(token));
        title.setTextColor(enabled ? activity.textColor() : activity.subtextColor());
        title.setTextSize(15);
        textColumn.addView(title, activity.matchWrap());

        TextView value = new TextView(activity);
        value.setText("{" + token + "}");
        value.setTextColor(enabled ? activity.primaryColor() : activity.subtextColor());
        value.setTextSize(12);
        value.setPadding(0, activity.dp(4), 0, 0);
        textColumn.addView(value, activity.matchWrap());
        row.addView(textColumn, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView state = activity.chip(
                enabled ? "已启用" : "未启用",
                enabled ? activity.primaryColor() : activity.surfaceStrongColor(),
                enabled ? Color.WHITE : activity.primaryColor());
        row.addView(state, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        activity.setTapClickListener(row, v -> toggleToken(token));
        row.setOnLongClickListener(v -> {
            String currentToken = v.getTag() instanceof String ? (String) v.getTag() : "";
            ClipData data = ClipData.newPlainText("clock_expression_token", currentToken);
            View.DragShadowBuilder shadow = new View.DragShadowBuilder(v);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                v.startDragAndDrop(data, shadow, v, 0);
            } else {
                v.startDrag(data, shadow, v, 0);
            }
            v.setAlpha(0.55f);
            return true;
        });
        row.setOnDragListener(this::handleRowDrag);
        return row;
    }

    private boolean handleRowDrag(View target, DragEvent event) {
        if (!(target.getTag() instanceof String)) {
            return false;
        }
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return event.getLocalState() instanceof View
                        && ((View) event.getLocalState()).getTag() instanceof String;
            case DragEvent.ACTION_DRAG_ENTERED:
                target.setBackground(buildRowBackground(enabledTokens.contains(target.getTag()), true));
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
                target.setBackground(buildRowBackground(enabledTokens.contains(target.getTag()), false));
                return true;
            case DragEvent.ACTION_DROP:
                target.setBackground(buildRowBackground(enabledTokens.contains(target.getTag()), false));
                Object localState = event.getLocalState();
                if (!(localState instanceof View) || !((((View) localState).getTag()) instanceof String)) {
                    return false;
                }
                moveToken((String) ((View) localState).getTag(), (String) target.getTag());
                renderEditor();
                return true;
            case DragEvent.ACTION_DRAG_ENDED:
                target.setBackground(buildRowBackground(enabledTokens.contains(target.getTag()), false));
                Object draggedView = event.getLocalState();
                if (draggedView instanceof View) {
                    ((View) draggedView).setAlpha(1f);
                }
                renderEditor();
                return true;
            default:
                return true;
        }
    }

    private void moveToken(String fromToken, String toToken) {
        if (TextUtils.isEmpty(fromToken) || TextUtils.isEmpty(toToken) || fromToken.equals(toToken)) {
            return;
        }
        int fromIndex = tokenOrder.indexOf(fromToken);
        int toIndex = tokenOrder.indexOf(toToken);
        if (fromIndex < 0 || toIndex < 0) {
            return;
        }
        tokenOrder.remove(fromIndex);
        if (fromIndex < toIndex) {
            toIndex--;
        }
        tokenOrder.add(toIndex, fromToken);
    }

    private void toggleToken(String token) {
        if (TextUtils.isEmpty(token) || !isValidToken(token)) {
            return;
        }
        if (enabledTokens.contains(token)) {
            enabledTokens.remove(token);
        } else {
            enabledTokens.add(token);
        }
        renderEditor();
    }

    private void updatePreview() {
        if (previewView == null) {
            return;
        }
        String format = buildFormat();
        previewView.setText(TextUtils.isEmpty(format) ? "未设置" : format);
    }

    private void applyDraft() {
        String format = buildFormat();
        SharedPreferences.Editor editor = activity.prefs().edit();
        editor.putString(SettingsStore.KEY_CLOCK_CUSTOM_FORMAT, format);
        editor.putString(SettingsStore.KEY_CLOCK_EXPRESSION_TOKEN_ORDER, serializeTokenOrder());
        editor.apply();
        SettingsStore.notifyChanged(activity);
        activity.invalidatePreview();
        activity.showToast(TextUtils.isEmpty(format) ? "已清空自定义时间表达式" : "自定义时间表达式已应用");
    }

    private void clearDraft() {
        enabledTokens.clear();
        renderEditor();
        activity.showToast("当前表达式已清空，点应用后才会写入设置");
    }

    private String buildFormat() {
        if (enabledTokens.isEmpty() || tokenOrder.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < tokenOrder.size(); i++) {
            String token = tokenOrder.get(i);
            if (!isValidToken(token) || !enabledTokens.contains(token)) {
                continue;
            }
            String previous = findPreviousEnabledToken(i);
            if (builder.length() > 0) {
                builder.append(resolveSeparator(previous, token));
            }
            builder.append('{').append(token).append('}');
        }
        return builder.toString();
    }

    private String findPreviousEnabledToken(int index) {
        for (int i = index - 1; i >= 0; i--) {
            String token = tokenOrder.get(i);
            if (isValidToken(token) && enabledTokens.contains(token)) {
                return token;
            }
        }
        return null;
    }

    private ArrayList<String> parseStoredOrder(String raw) {
        ArrayList<String> order = new ArrayList<>();
        if (TextUtils.isEmpty(raw)) {
            return order;
        }
        String[] tokens = raw.split(",");
        for (String token : tokens) {
            String normalized = token == null ? "" : token.trim();
            if (isValidToken(normalized) && !order.contains(normalized)) {
                order.add(normalized);
            }
        }
        return order;
    }

    private void appendMissingTokens(ArrayList<String> order) {
        for (String[] rowTokens : MainActivity.CLOCK_EXPRESSION_TOKEN_ROWS) {
            for (String token : rowTokens) {
                if (isValidToken(token) && !order.contains(token)) {
                    order.add(token);
                }
            }
        }
    }

    private String serializeTokenOrder() {
        if (tokenOrder.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < tokenOrder.size(); i++) {
            String token = tokenOrder.get(i);
            if (!isValidToken(token)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(token);
        }
        return builder.toString();
    }

    private android.graphics.drawable.GradientDrawable buildRowBackground(boolean enabled, boolean active) {
        int background = active
                ? activity.surfaceStrongColor()
                : (enabled ? activity.featureSurfaceColor() : activity.surfaceColor());
        int stroke = active
                ? activity.featureStrokeColor()
                : (enabled ? activity.primaryColor() : activity.strokeColor());
        return activity.outlinedRect(background, stroke, 1, 20);
    }

    private String resolveSeparator(String previous, String current) {
        if (TextUtils.isEmpty(previous) || TextUtils.isEmpty(current)) {
            return "";
        }
        if (isClockTimeUnitToken(previous) && isClockTimeUnitToken(current)) {
            if (("HH".equals(previous) || "H".equals(previous) || "hh".equals(previous) || "h".equals(previous))
                    && "mm".equals(current)) {
                return ":";
            }
            if ("mm".equals(previous) && "ss".equals(current)) {
                return ":";
            }
        }
        return " ";
    }

    private boolean isClockTimeUnitToken(String token) {
        return "HH".equals(token) || "H".equals(token)
                || "hh".equals(token) || "h".equals(token)
                || "mm".equals(token) || "ss".equals(token);
    }

    private boolean isValidToken(String token) {
        return "HH".equals(token)
                || "H".equals(token)
                || "hh".equals(token)
                || "h".equals(token)
                || "mm".equals(token)
                || "ss".equals(token)
                || "week".equals(token)
                || "week_short".equals(token)
                || "week_1".equals(token)
                || "ampm".equals(token)
                || "period".equals(token)
                || "branch".equals(token)
                || "branch_alias".equals(token);
    }

    private String getTokenLabel(String token) {
        if ("HH".equals(token)) return "24小时";
        if ("H".equals(token)) return "24小时单数";
        if ("hh".equals(token)) return "12小时";
        if ("h".equals(token)) return "12小时单数";
        if ("mm".equals(token)) return "分钟";
        if ("ss".equals(token)) return "秒";
        if ("week".equals(token)) return "星期";
        if ("week_short".equals(token)) return "周";
        if ("week_1".equals(token)) return "周简写";
        if ("ampm".equals(token)) return "AM/PM";
        if ("period".equals(token)) return "时段词";
        if ("branch".equals(token)) return "地支";
        if ("branch_alias".equals(token)) return "传统别称";
        return token;
    }
}
