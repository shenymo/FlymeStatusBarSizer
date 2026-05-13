package com.example.flymestatusbarsizer;

import android.content.ClipData;
import android.os.Build;
import android.text.TextUtils;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;

final class ClockExpressionEditor {
    private final MainActivity activity;
    private final ArrayList<String> draftTokens = new ArrayList<>();
    private final HashMap<String, TextView> tokenButtons = new HashMap<>();

    private LinearLayout orderContainer;
    private TextView previewView;

    ClockExpressionEditor(MainActivity activity) {
        this.activity = activity;
    }

    LinearLayout buildPage() {
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);

        activity.addProfileSectionHeader(page, "表达式编辑",
                "点下面的按钮加入表达式。已加入的项支持长按拖动排序。小时、分钟、秒连续排列时会自动补冒号，不需要单独插入。");
        TextView hint = new TextView(activity);
        hint.setText("当前支持：小时、分钟、秒、星期、AM/PM、时段词、十二时辰地支和传统别称。");
        hint.setTextColor(activity.subtextColor());
        hint.setTextSize(13);
        hint.setPadding(0, activity.dp(10), 0, 0);
        page.addView(hint, activity.matchWrap());

        page.addView(buildButtonPanel(), activity.matchWrapWithTop(12));

        TextView orderTitle = new TextView(activity);
        orderTitle.setText("当前顺序");
        orderTitle.setTextColor(activity.primaryColor());
        orderTitle.setTextSize(15);
        orderTitle.setPadding(0, activity.dp(16), 0, 0);
        page.addView(orderTitle, activity.matchWrap());

        TextView orderHint = new TextView(activity);
        orderHint.setText("点击已选项可移除，长按可拖动排序。");
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
                "保存当前按钮顺序生成的表达式，并通知 SystemUI 立即刷新状态栏时间。",
                "应用", this::applyDraft);
        activity.addDivider(page);
        activity.addActionButtonRow(page, "清空当前表达式",
                "清空后会只保留系统原始时间显示。",
                "清空", this::clearDraft);
        return page;
    }

    private View buildButtonPanel() {
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        tokenButtons.clear();
        for (String[] rowTokens : MainActivity.CLOCK_EXPRESSION_TOKEN_ROWS) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, panel.getChildCount() == 0 ? 0 : activity.dp(10), 0, 0);
            for (int i = 0; i < rowTokens.length; i++) {
                String token = rowTokens[i];
                TextView button = buildTokenButton(token);
                tokenButtons.put(token, button);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                if (i > 0) {
                    lp.leftMargin = activity.dp(8);
                }
                row.addView(button, lp);
            }
            panel.addView(row, activity.matchWrap());
        }
        return panel;
    }

    private TextView buildTokenButton(String token) {
        TextView button = new TextView(activity);
        button.setText(getTokenLabel(token));
        button.setTextColor(activity.primaryColor());
        button.setTextSize(13);
        button.setGravity(Gravity.CENTER);
        button.setPadding(activity.dp(10), activity.dp(12), activity.dp(10), activity.dp(12));
        button.setBackground(activity.outlinedRect(activity.surfaceColor(), activity.strokeColor(), 1, 18));
        button.setTag(token);
        activity.setTapClickListener(button, v -> {
            Object tag = v.getTag();
            if (!(tag instanceof String)) {
                return;
            }
            String currentToken = (String) tag;
            if (draftTokens.contains(currentToken)) {
                removeToken(currentToken);
            } else {
                draftTokens.add(currentToken);
                renderEditor();
            }
        });
        return button;
    }

    private void loadDraft() {
        draftTokens.clear();
        String raw = activity.readStringSetting(
                SettingsStore.KEY_CLOCK_CUSTOM_FORMAT,
                SettingsStore.DEFAULT_CLOCK_CUSTOM_FORMAT);
        if (!TextUtils.isEmpty(raw)) {
            Matcher matcher = MainActivity.CLOCK_EXPRESSION_TOKEN_PATTERN.matcher(raw);
            while (matcher.find()) {
                String token = matcher.group(1);
                if (isValidToken(token)) {
                    draftTokens.add(token);
                }
            }
        }
        syncButtons();
    }

    private void renderEditor() {
        syncButtons();
        updatePreview();
        if (orderContainer == null) {
            return;
        }
        orderContainer.removeAllViews();
        if (draftTokens.isEmpty()) {
            TextView empty = new TextView(activity);
            empty.setText("当前为空，状态栏时间会回退到系统原始时间显示。");
            empty.setTextColor(activity.subtextColor());
            empty.setTextSize(13);
            empty.setPadding(0, activity.dp(4), 0, 0);
            orderContainer.addView(empty, activity.matchWrap());
            return;
        }
        for (int i = 0; i < draftTokens.size(); i++) {
            String token = draftTokens.get(i);
            orderContainer.addView(buildOrderRow(token), activity.matchWrap());
            if (i < draftTokens.size() - 1) {
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
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(activity.dp(14), activity.dp(12), activity.dp(14), activity.dp(12));
        row.setBackground(activity.outlinedRect(activity.surfaceColor(), activity.strokeColor(), 1, 20));
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
        title.setTextColor(activity.textColor());
        title.setTextSize(15);
        textColumn.addView(title, activity.matchWrap());

        TextView value = new TextView(activity);
        value.setText("{" + token + "}");
        value.setTextColor(activity.subtextColor());
        value.setTextSize(12);
        value.setPadding(0, activity.dp(4), 0, 0);
        textColumn.addView(value, activity.matchWrap());
        row.addView(textColumn, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView remove = activity.chip("点击移除", activity.surfaceStrongColor(), activity.primaryColor());
        row.addView(remove, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        activity.setTapClickListener(row, v -> {
            Object tag = v.getTag();
            if (tag instanceof String) {
                removeToken((String) tag);
            }
        });
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
                target.setBackground(activity.outlinedRect(
                        activity.surfaceStrongColor(),
                        activity.featureStrokeColor(),
                        1,
                        20));
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
                target.setBackground(activity.outlinedRect(activity.surfaceColor(), activity.strokeColor(), 1, 20));
                return true;
            case DragEvent.ACTION_DROP:
                target.setBackground(activity.outlinedRect(activity.surfaceColor(), activity.strokeColor(), 1, 20));
                Object localState = event.getLocalState();
                if (!(localState instanceof View) || !((((View) localState).getTag()) instanceof String)) {
                    return false;
                }
                moveToken((String) ((View) localState).getTag(), (String) target.getTag());
                syncEditorRows();
                updatePreview();
                syncButtons();
                return true;
            case DragEvent.ACTION_DRAG_ENDED:
                target.setBackground(activity.outlinedRect(activity.surfaceColor(), activity.strokeColor(), 1, 20));
                Object draggedView = event.getLocalState();
                if (draggedView instanceof View) {
                    ((View) draggedView).setAlpha(1f);
                }
                syncEditorRows();
                updatePreview();
                syncButtons();
                return true;
            default:
                return true;
        }
    }

    private void moveToken(String fromToken, String toToken) {
        if (TextUtils.isEmpty(fromToken) || TextUtils.isEmpty(toToken) || fromToken.equals(toToken)) {
            return;
        }
        int fromIndex = draftTokens.indexOf(fromToken);
        int toIndex = draftTokens.indexOf(toToken);
        if (fromIndex < 0 || toIndex < 0) {
            return;
        }
        draftTokens.remove(fromIndex);
        if (fromIndex < toIndex) {
            toIndex--;
        }
        draftTokens.add(toIndex, fromToken);
    }

    private void syncEditorRows() {
        if (orderContainer == null) {
            return;
        }
        int orderIndex = 0;
        for (int i = 0; i < orderContainer.getChildCount(); i++) {
            View child = orderContainer.getChildAt(i);
            if (!(child instanceof LinearLayout) || orderIndex >= draftTokens.size()) {
                continue;
            }
            LinearLayout row = (LinearLayout) child;
            String token = draftTokens.get(orderIndex++);
            row.setTag(token);
            row.setAlpha(1f);
            row.setBackground(activity.outlinedRect(activity.surfaceColor(), activity.strokeColor(), 1, 20));
            View titleView = null;
            View valueView = null;
            if (row.getChildCount() > 1 && row.getChildAt(1) instanceof LinearLayout) {
                LinearLayout textColumn = (LinearLayout) row.getChildAt(1);
                titleView = textColumn.getChildCount() > 0 ? textColumn.getChildAt(0) : null;
                valueView = textColumn.getChildCount() > 1 ? textColumn.getChildAt(1) : null;
            }
            if (titleView instanceof TextView) {
                ((TextView) titleView).setText(getTokenLabel(token));
            }
            if (valueView instanceof TextView) {
                ((TextView) valueView).setText("{" + token + "}");
            }
        }
    }

    private void removeToken(String token) {
        int index = draftTokens.indexOf(token);
        if (index < 0) {
            return;
        }
        draftTokens.remove(index);
        renderEditor();
    }

    private void updatePreview() {
        if (previewView == null) {
            return;
        }
        String format = buildFormat();
        previewView.setText(TextUtils.isEmpty(format) ? "未设置" : format);
    }

    private void syncButtons() {
        for (String token : tokenButtons.keySet()) {
            TextView button = tokenButtons.get(token);
            if (button == null) {
                continue;
            }
            boolean selected = draftTokens.contains(token);
            button.setTextColor(selected ? android.graphics.Color.WHITE : activity.primaryColor());
            button.setBackground(activity.outlinedRect(
                    selected ? activity.primaryColor() : activity.surfaceColor(),
                    selected ? activity.primaryColor() : activity.strokeColor(),
                    1,
                    18));
        }
    }

    private void applyDraft() {
        String format = buildFormat();
        activity.putStringSetting(SettingsStore.KEY_CLOCK_CUSTOM_FORMAT, format);
        activity.showToast(TextUtils.isEmpty(format) ? "已清空自定义时间表达式" : "自定义时间表达式已应用");
    }

    private void clearDraft() {
        draftTokens.clear();
        renderEditor();
        activity.showToast("当前表达式已清空，点应用后才会写入设置");
    }

    private String buildFormat() {
        if (draftTokens.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < draftTokens.size(); i++) {
            String token = draftTokens.get(i);
            if (!isValidToken(token)) {
                continue;
            }
            String previous = findPreviousToken(i);
            if (builder.length() > 0) {
                builder.append(resolveSeparator(previous, token));
            }
            builder.append('{').append(token).append('}');
        }
        return builder.toString();
    }

    private String findPreviousToken(int index) {
        for (int i = index - 1; i >= 0; i--) {
            String token = draftTokens.get(i);
            if (isValidToken(token)) {
                return token;
            }
        }
        return null;
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
