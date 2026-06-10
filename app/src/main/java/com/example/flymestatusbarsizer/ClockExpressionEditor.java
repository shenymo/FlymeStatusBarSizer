package com.example.flymestatusbarsizer;

import android.content.ClipData;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.TextUtils;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.regex.Matcher;

final class ClockExpressionEditor {
    private static final int TOKEN_COLUMN_COUNT = 3;
    private static final long QUICK_SWIPE_MAX_DURATION_MS = 220L;

    private final MainActivity activity;
    private final ArrayList<String> tokenOrder = new ArrayList<>();
    private final ArrayList<String> enabledTokens = new ArrayList<>();
    private int dragTouchSlop;

    private LinearLayout orderContainer;
    private TextView previewView;

    ClockExpressionEditor(MainActivity activity) {
        this.activity = activity;
    }

    LinearLayout buildPage() {
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);

        activity.addProfileSectionHeader(page, "表达式编辑",
                "当前支持：小时、分钟、秒、星期、AM/PM、时段词、十二时辰地支和传统别称。单击切换启用；快速左右滑动切换 24 小时、12 小时、星期写法；长按可拖动排序。");
        ensureDragTouchSlop();

        TextView orderTitle = new TextView(activity);
        orderTitle.setText("表达式列表");
        orderTitle.setTextColor(activity.primaryColor());
        orderTitle.setTextSize(15);
        orderTitle.setPadding(0, activity.dp(14), 0, 0);
        page.addView(orderTitle, activity.matchWrap());

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
        normalizeTokenOrder();
        syncTokenOrderToEnabledVariants();
        appendMissingTokens(tokenOrder);
        normalizeEnabledTokens();
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
        for (int i = 0; i < tokenOrder.size(); i += TOKEN_COLUMN_COUNT) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            if (i > 0) {
                row.setPadding(0, activity.dp(6), 0, 0);
            }
            for (int j = 0; j < TOKEN_COLUMN_COUNT; j++) {
                int index = i + j;
                LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                if (j > 0) {
                    chipLp.leftMargin = activity.dp(6);
                }
                if (index >= tokenOrder.size()) {
                    View spacer = new View(activity);
                    row.addView(spacer, chipLp);
                    continue;
                }
                row.addView(buildOrderChip(tokenOrder.get(index)), chipLp);
            }
            orderContainer.addView(row, activity.matchWrap());
        }
    }

    private View buildOrderChip(String token) {
        boolean enabled = enabledTokens.contains(token);
        RowTouchState touchState = new RowTouchState();
        TextView chip = new TextView(activity);
        chip.setText(getTokenLabel(token));
        chip.setTextColor(enabled ? activity.textColor() : activity.subtextColor());
        chip.setTextSize(12);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(activity.dp(8), activity.dp(8), activity.dp(8), activity.dp(8));
        chip.setMinHeight(activity.dp(40));
        chip.setBackground(buildChipBackground(enabled, false));
        chip.setTag(token);

        activity.setTapClickListener(chip, v -> toggleToken(token));
        chip.setOnLongClickListener(this::startRowDrag);
        chip.setOnTouchListener((v, event) -> handleRowTouch(v, event, touchState));
        chip.setOnDragListener(this::handleRowDrag);
        return chip;
    }

    private boolean handleRowTouch(View view, MotionEvent event, RowTouchState state) {
        if (view == null || event == null || state == null) {
            return false;
        }
        ensureDragTouchSlop();
        ViewParent parent = view.getParent();
        String token = view.getTag() instanceof String ? (String) view.getTag() : "";
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                state.downX = event.getX();
                state.downY = event.getY();
                state.downTimeMs = event.getEventTime();
                state.swipeDirection = 0;
                return false;
            case MotionEvent.ACTION_MOVE:
                if (state.swipeDirection != 0) {
                    return true;
                }
                float dx = Math.abs(event.getX() - state.downX);
                float dy = Math.abs(event.getY() - state.downY);
                if (Math.max(dx, dy) < dragTouchSlop) {
                    return false;
                }
                if (supportsVariantSwitch(token)
                        && dx > dy
                        && event.getEventTime() - state.downTimeMs <= QUICK_SWIPE_MAX_DURATION_MS) {
                    if (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(true);
                    }
                    state.swipeDirection = event.getX() > state.downX ? 1 : -1;
                    return true;
                }
                return false;
            case MotionEvent.ACTION_UP:
                if (state.swipeDirection != 0) {
                    if (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(false);
                    }
                    int direction = state.swipeDirection;
                    state.swipeDirection = 0;
                    if (cycleTokenVariant(token, direction > 0)) {
                        activity.performTapHaptic(view);
                    }
                    return true;
                }
            case MotionEvent.ACTION_CANCEL:
                state.swipeDirection = 0;
                if (parent != null) {
                    parent.requestDisallowInterceptTouchEvent(false);
                }
                return false;
            default:
                return false;
        }
    }

    private void ensureDragTouchSlop() {
        if (dragTouchSlop <= 0) {
            dragTouchSlop = Math.max(activity.dp(4), ViewConfiguration.get(activity).getScaledTouchSlop() / 2);
        }
    }

    private boolean startRowDrag(View view) {
        String currentToken = view != null && view.getTag() instanceof String ? (String) view.getTag() : "";
        if (TextUtils.isEmpty(currentToken)) {
            return false;
        }
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        ClipData data = ClipData.newPlainText("clock_expression_token", currentToken);
        View.DragShadowBuilder shadow = new View.DragShadowBuilder(view);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            view.startDragAndDrop(data, shadow, view, 0);
        } else {
            view.startDrag(data, shadow, view, 0);
        }
        view.setAlpha(0.55f);
        return true;
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
                target.setBackground(buildChipBackground(enabledTokens.contains(target.getTag()), true));
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
                target.setBackground(buildChipBackground(enabledTokens.contains(target.getTag()), false));
                return true;
            case DragEvent.ACTION_DROP:
                target.setBackground(buildChipBackground(enabledTokens.contains(target.getTag()), false));
                Object localState = event.getLocalState();
                if (!(localState instanceof View) || !((((View) localState).getTag()) instanceof String)) {
                    return false;
                }
                moveToken((String) ((View) localState).getTag(), (String) target.getTag());
                return true;
            case DragEvent.ACTION_DRAG_ENDED:
                target.setBackground(buildChipBackground(enabledTokens.contains(target.getTag()), false));
                Object draggedView = event.getLocalState();
                if (draggedView instanceof View) {
                    ((View) draggedView).setAlpha(1f);
                    if (target == draggedView) {
                        scheduleRenderEditor();
                    }
                }
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

    private boolean cycleTokenVariant(String token, boolean forward) {
        String nextToken = resolveAdjacentVariantToken(token, forward);
        if (TextUtils.isEmpty(nextToken) || TextUtils.equals(nextToken, token)) {
            return false;
        }
        int index = tokenOrder.indexOf(token);
        if (index >= 0) {
            tokenOrder.set(index, nextToken);
        }
        if (enabledTokens.contains(token)) {
            enabledTokens.remove(token);
            if (!enabledTokens.contains(nextToken)) {
                enabledTokens.add(nextToken);
            }
        }
        renderEditor();
        return true;
    }

    private void updatePreview() {
        if (previewView == null) {
            return;
        }
        String format = buildFormat();
        previewView.setText(TextUtils.isEmpty(format) ? "未设置" : format);
    }

    private void scheduleRenderEditor() {
        View host = orderContainer != null ? orderContainer : previewView;
        if (host != null) {
            host.post(this::renderEditor);
        } else {
            renderEditor();
        }
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
        String[] defaults = new String[]{
                "HH", "hh", "mm", "ss", "week", "ampm", "period", "branch", "branch_alias"
        };
        for (String token : defaults) {
            if (isValidToken(token) && !containsTokenGroup(order, token)) {
                order.add(token);
            }
        }
    }

    private void normalizeTokenOrder() {
        ArrayList<String> normalized = new ArrayList<>();
        for (int i = 0; i < tokenOrder.size(); i++) {
            String token = tokenOrder.get(i);
            if (!isValidToken(token) || containsTokenGroup(normalized, token)) {
                continue;
            }
            normalized.add(token);
        }
        tokenOrder.clear();
        tokenOrder.addAll(normalized);
    }

    private void normalizeEnabledTokens() {
        ArrayList<String> normalized = new ArrayList<>();
        for (int i = 0; i < tokenOrder.size(); i++) {
            String token = tokenOrder.get(i);
            if (!TextUtils.isEmpty(findEnabledVariantForGroup(token)) && !normalized.contains(token)) {
                normalized.add(token);
            }
        }
        enabledTokens.clear();
        enabledTokens.addAll(normalized);
    }

    private void syncTokenOrderToEnabledVariants() {
        for (int i = 0; i < tokenOrder.size(); i++) {
            String token = tokenOrder.get(i);
            String enabledVariant = findEnabledVariantForGroup(token);
            if (!TextUtils.isEmpty(enabledVariant)) {
                tokenOrder.set(i, enabledVariant);
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

    private android.graphics.drawable.GradientDrawable buildChipBackground(boolean enabled, boolean active) {
        int background = active
                ? activity.surfaceStrongColor()
                : (enabled ? activity.featureSurfaceColor() : activity.surfaceColor());
        int stroke = active
                ? activity.featureStrokeColor()
                : (enabled ? activity.primaryColor() : activity.strokeColor());
        return activity.outlinedRect(background, stroke, 1, 16);
    }

    private boolean supportsVariantSwitch(String token) {
        return is24HourToken(token) || is12HourToken(token) || isWeekToken(token);
    }

    private String resolveAdjacentVariantToken(String token, boolean forward) {
        String[] variants = resolveVariantGroup(token);
        if (variants == null || variants.length <= 1) {
            return token;
        }
        int currentIndex = -1;
        for (int i = 0; i < variants.length; i++) {
            if (TextUtils.equals(variants[i], token)) {
                currentIndex = i;
                break;
            }
        }
        if (currentIndex < 0) {
            return token;
        }
        int nextIndex = forward
                ? (currentIndex + 1) % variants.length
                : (currentIndex - 1 + variants.length) % variants.length;
        return variants[nextIndex];
    }

    private String[] resolveVariantGroup(String token) {
        if (is24HourToken(token)) {
            return new String[]{"HH", "H"};
        }
        if (is12HourToken(token)) {
            return new String[]{"hh", "h"};
        }
        if (isWeekToken(token)) {
            return new String[]{"week", "week_short", "week_1"};
        }
        return null;
    }

    private boolean containsTokenGroup(ArrayList<String> tokens, String candidate) {
        if (tokens == null || TextUtils.isEmpty(candidate)) {
            return false;
        }
        for (int i = 0; i < tokens.size(); i++) {
            if (isSameTokenGroup(tokens.get(i), candidate)) {
                return true;
            }
        }
        return false;
    }

    private String findEnabledVariantForGroup(String token) {
        if (TextUtils.isEmpty(token)) {
            return null;
        }
        for (int i = 0; i < enabledTokens.size(); i++) {
            String enabledToken = enabledTokens.get(i);
            if (isSameTokenGroup(enabledToken, token)) {
                return enabledToken;
            }
        }
        return null;
    }

    private boolean isSameTokenGroup(String first, String second) {
        if (TextUtils.isEmpty(first) || TextUtils.isEmpty(second)) {
            return false;
        }
        if (TextUtils.equals(first, second)) {
            return true;
        }
        return is24HourToken(first) && is24HourToken(second)
                || is12HourToken(first) && is12HourToken(second)
                || isWeekToken(first) && isWeekToken(second);
    }

    private boolean is24HourToken(String token) {
        return "HH".equals(token) || "H".equals(token);
    }

    private boolean is12HourToken(String token) {
        return "hh".equals(token) || "h".equals(token);
    }

    private boolean isWeekToken(String token) {
        return "week".equals(token) || "week_short".equals(token) || "week_1".equals(token);
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

    private static final class RowTouchState {
        private float downX;
        private float downY;
        private long downTimeMs;
        private int swipeDirection;
    }
}
