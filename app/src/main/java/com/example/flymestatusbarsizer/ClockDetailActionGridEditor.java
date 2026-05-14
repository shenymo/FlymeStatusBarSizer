package com.example.flymestatusbarsizer;

import com.example.flymestatusbarsizer.feature.clock.ClockDetailActionCodec;
import com.example.flymestatusbarsizer.feature.clock.ClockDetailActionEntry;
import com.example.flymestatusbarsizer.feature.clock.ClockDetailActionResolver;
import com.example.flymestatusbarsizer.feature.clock.ClockDetailActionSpec;
import com.example.flymestatusbarsizer.feature.clock.ClockDetailAssistantActionCatalog;

import android.app.AlertDialog;
import android.content.ClipData;
import android.graphics.Outline;
import android.os.Build;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

final class ClockDetailActionGridEditor {
    private static final String ACTION_DRAG_LABEL = "clock_detail_action_slot";

    private final MainActivity activity;

    ClockDetailActionGridEditor(MainActivity activity) {
        this.activity = activity;
    }

    void show() {
        final ClockDetailActionSpec[] workingSpecs = ClockDetailAssistantActionCatalog.normalizePresetGrid(
                ClockDetailActionCodec.decode(activity.readStringSetting(
                        SettingsStore.KEY_CLOCK_DETAIL_ACTION_GRID_ITEMS_JSON,
                        SettingsStore.DEFAULT_CLOCK_DETAIL_ACTION_GRID_ITEMS_JSON)));
        final ActionIconCellView[] cellViews = new ActionIconCellView[ClockDetailActionSpec.SLOT_COUNT];
        final TextView orderSummaryView = new TextView(activity);

        ScrollView scrollView = new ScrollView(activity);
        scrollView.setFillViewport(true);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int horizontalPadding = activity.dp(6);
        content.setPadding(horizontalPadding, activity.dp(4), horizontalPadding, activity.dp(4));
        scrollView.addView(
                content,
                new ScrollView.LayoutParams(
                        ScrollView.LayoutParams.MATCH_PARENT,
                        ScrollView.LayoutParams.WRAP_CONTENT));

        content.addView(buildPresetCard(workingSpecs, cellViews, orderSummaryView), activity.matchWrap());
        content.addView(buildEditorCard(workingSpecs, cellViews, orderSummaryView), activity.matchWrapWithTop(12));

        refreshViews(workingSpecs, cellViews, orderSummaryView);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("调整时间弹窗一行四列图标入口")
                .setMessage("长按图标拖到目标位置；拖动只修改草稿，点“应用”后才会保存到配置。")
                .setView(scrollView)
                .setNegativeButton("取消", null)
                .setPositiveButton("应用", (dialogInterface, which) -> {
                    ClockDetailActionSpec[] normalized =
                            ClockDetailAssistantActionCatalog.normalizePresetGrid(workingSpecs);
                    activity.putStringSetting(
                            SettingsStore.KEY_CLOCK_DETAIL_ACTION_GRID_ITEMS_JSON,
                            ClockDetailActionCodec.encode(normalized));
                    activity.showToast("时间弹窗图标入口顺序已保存");
                })
                .show();
        activity.attachDialogButtonHaptics(dialog);
    }

    private View buildPresetCard(
            ClockDetailActionSpec[] workingSpecs,
            ActionIconCellView[] cellViews,
            TextView orderSummaryView) {
        LinearLayout card = activity.card(activity.surfaceSoftColor(), activity.strokeColor(), 24);
        activity.addProfileSectionHeader(
                card,
                "预设动作",
                "固定保留微信扫一扫、微信收付款、支付宝扫一扫、支付宝付款码四个入口。");
        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.addView(buildChipButton("恢复默认顺序", v -> {
            activity.performTapHaptic(v);
            applySpecs(workingSpecs, ClockDetailAssistantActionCatalog.createAssistantPresetGrid());
            refreshViews(workingSpecs, cellViews, orderSummaryView);
            activity.showToast("已恢复默认顺序");
        }), chipButtonLayoutParams(false));
        card.addView(actions, activity.matchWrapWithTop(12));
        return card;
    }

    private View buildEditorCard(
            ClockDetailActionSpec[] workingSpecs,
            ActionIconCellView[] cellViews,
            TextView orderSummaryView) {
        LinearLayout card = activity.card(activity.featureSurfaceColor(), activity.featureStrokeColor(), 24);
        activity.addProfileSectionHeader(
                card,
                "拖动排序",
                "长按任意图标直接拖到目标位置；灰色表示当前设备还没有解析到可启动目标。");
        card.addView(buildIconGrid(workingSpecs, cellViews, orderSummaryView), activity.matchWrapWithTop(12));
        orderSummaryView.setTextColor(activity.subtextColor());
        orderSummaryView.setTextSize(12);
        orderSummaryView.setLineSpacing(0f, 1.08f);
        orderSummaryView.setPadding(0, activity.dp(12), 0, 0);
        card.addView(orderSummaryView, activity.matchWrap());
        return card;
    }

    private View buildIconGrid(
            ClockDetailActionSpec[] workingSpecs,
            ActionIconCellView[] cellViews,
            TextView orderSummaryView) {
        LinearLayout grid = new LinearLayout(activity);
        grid.setOrientation(LinearLayout.HORIZONTAL);
        grid.setGravity(Gravity.CENTER);
        for (int slot = 0; slot < ClockDetailActionSpec.SLOT_COUNT; slot++) {
            ActionIconCellView cell = buildIconCell(workingSpecs, cellViews, orderSummaryView);
            cellViews[slot] = cell;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            if (slot > 0) {
                params.leftMargin = activity.dp(8);
            }
            grid.addView(cell.root, params);
        }
        return grid;
    }

    private ActionIconCellView buildIconCell(
            ClockDetailActionSpec[] workingSpecs,
            ActionIconCellView[] cellViews,
            TextView orderSummaryView) {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setMinimumHeight(activity.dp(44));
        root.setPadding(activity.dp(4), activity.dp(4), activity.dp(4), activity.dp(4));
        root.setOnLongClickListener(this::startIconDrag);
        root.setOnDragListener((target, event) ->
                handleIconDrag(workingSpecs, cellViews, orderSummaryView, target, event));

        ImageView iconView = new ImageView(activity);
        int iconSize = activity.dp(30);
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        styleRoundedIcon(iconView, 10);
        root.addView(iconView, new LinearLayout.LayoutParams(iconSize, iconSize));
        return new ActionIconCellView(root, iconView);
    }

    private LinearLayout.LayoutParams chipButtonLayoutParams(boolean withStartMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        if (withStartMargin) {
            params.leftMargin = activity.dp(8);
        }
        return params;
    }

    private TextView buildChipButton(String text, View.OnClickListener listener) {
        TextView button = activity.chip(text, activity.surfaceColor(), activity.primaryColor());
        button.setBackground(activity.outlinedRect(
                activity.surfaceColor(),
                activity.featureStrokeColor(),
                1,
                99));
        activity.setTapClickListener(button, listener);
        return button;
    }

    private void refreshViews(
            ClockDetailActionSpec[] specs,
            ActionIconCellView[] cellViews,
            TextView orderSummaryView) {
        ClockDetailActionEntry[] entries = ClockDetailActionResolver.resolveEntries(activity, specs);
        StringBuilder orderText = new StringBuilder("当前顺序：");
        StringBuilder invalidText = new StringBuilder();
        for (int slot = 0; slot < ClockDetailActionSpec.SLOT_COUNT; slot++) {
            ClockDetailActionSpec spec = specs[slot];
            ClockDetailActionEntry entry = entries[slot];
            if (slot > 0) {
                orderText.append(" · ");
            }
            orderText.append(resolveActionTitle(spec, entry));
            if (entry == null || !entry.valid) {
                if (invalidText.length() > 0) {
                    invalidText.append("、");
                }
                invalidText.append(resolveActionTitle(spec, entry));
            }
            updateIconCell(cellViews[slot], spec, entry);
        }
        if (orderSummaryView != null) {
            if (invalidText.length() > 0) {
                orderText.append("\n灰色表示当前设备还没有解析到这些入口：")
                        .append(invalidText);
            } else {
                orderText.append("\n当前四个入口都已解析到可启动目标。");
            }
            orderSummaryView.setText(orderText);
        }
    }

    private void updateIconCell(
            ActionIconCellView cell,
            ClockDetailActionSpec spec,
            ClockDetailActionEntry entry) {
        if (cell == null) {
            return;
        }
        boolean valid = entry != null && entry.valid;
        String action = spec == null ? "" : spec.assistantAction;
        cell.root.setTag(action);
        cell.root.setContentDescription(resolveActionTitle(spec, entry));
        cell.root.setAlpha(valid ? 1f : 0.78f);
        cell.root.setScaleX(1f);
        cell.root.setScaleY(1f);
        cell.root.setBackground(null);
        cell.iconView.setImageDrawable(entry != null && entry.hasIcon()
                ? entry.icon
                : ClockDetailAssistantActionCatalog.resolveActionIcon(activity, action));
        cell.iconView.setAlpha(valid ? 1f : 0.58f);
    }

    private void styleRoundedIcon(ImageView iconView, int radiusDp) {
        if (iconView == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        final int radiusPx = activity.dp(radiusDp);
        iconView.setClipToOutline(true);
        iconView.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radiusPx);
            }
        });
    }

    private String resolveActionTitle(
            ClockDetailActionSpec spec,
            ClockDetailActionEntry entry) {
        if (entry != null && entry.hasDisplayLabel()) {
            return entry.resolvedLabel;
        }
        if (spec == null) {
            return "";
        }
        String title = spec.title;
        if (title != null && !title.trim().isEmpty()) {
            return title.trim();
        }
        return ClockDetailAssistantActionCatalog.resolveActionTitle(spec.assistantAction);
    }

    private boolean moveSpec(ClockDetailActionSpec[] specs, String fromAction, String toAction) {
        if (specs == null
                || fromAction == null
                || toAction == null
                || fromAction.trim().isEmpty()
                || toAction.trim().isEmpty()
                || fromAction.equals(toAction)) {
            return false;
        }
        String[] actions = new String[ClockDetailActionSpec.SLOT_COUNT];
        for (int slot = 0; slot < actions.length; slot++) {
            ClockDetailActionSpec spec = slot < specs.length ? specs[slot] : null;
            actions[slot] = spec == null ? "" : spec.assistantAction;
        }
        int from = indexOfAction(actions, fromAction);
        int to = indexOfAction(actions, toAction);
        if (from < 0 || to < 0 || from == to) {
            return false;
        }
        String moving = actions[from];
        if (from < to) {
            for (int slot = from; slot < to; slot++) {
                actions[slot] = actions[slot + 1];
            }
        } else {
            for (int slot = from; slot > to; slot--) {
                actions[slot] = actions[slot - 1];
            }
        }
        actions[to] = moving;
        applySpecs(specs, ClockDetailAssistantActionCatalog.createGridForActionOrder(actions));
        return true;
    }

    private boolean startIconDrag(View view) {
        String action = view != null && view.getTag() instanceof String
                ? (String) view.getTag()
                : "";
        if (action.isEmpty()) {
            return false;
        }
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        ClipData data = ClipData.newPlainText(ACTION_DRAG_LABEL, action);
        View.DragShadowBuilder shadow = new View.DragShadowBuilder(view);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            view.startDragAndDrop(data, shadow, view, 0);
        } else {
            view.startDrag(data, shadow, view, 0);
        }
        view.setAlpha(0.46f);
        return true;
    }

    private boolean handleIconDrag(
            ClockDetailActionSpec[] workingSpecs,
            ActionIconCellView[] cellViews,
            TextView orderSummaryView,
            View target,
            DragEvent event) {
        if (!(target.getTag() instanceof String)) {
            return false;
        }
        ActionIconCellView targetCell = findCell(cellViews, target);
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return event.getLocalState() instanceof View
                        && ((View) event.getLocalState()).getTag() instanceof String;
            case DragEvent.ACTION_DRAG_ENTERED:
                applyHoverState(targetCell, true);
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
                applyHoverState(targetCell, false);
                return true;
            case DragEvent.ACTION_DROP:
                applyHoverState(targetCell, false);
                Object localState = event.getLocalState();
                if (!(localState instanceof View) || !(((View) localState).getTag() instanceof String)) {
                    return false;
                }
                if (moveSpec(
                        workingSpecs,
                        (String) ((View) localState).getTag(),
                        (String) target.getTag())) {
                    refreshViews(workingSpecs, cellViews, orderSummaryView);
                }
                return true;
            case DragEvent.ACTION_DRAG_ENDED:
                applyHoverState(targetCell, false);
                Object draggedView = event.getLocalState();
                if (draggedView instanceof View) {
                    ((View) draggedView).setAlpha(1f);
                }
                refreshViews(workingSpecs, cellViews, orderSummaryView);
                return true;
            default:
                return true;
        }
    }

    private void applyHoverState(ActionIconCellView cell, boolean hovered) {
        if (cell == null) {
            return;
        }
        cell.root.setScaleX(hovered ? 1.05f : 1f);
        cell.root.setScaleY(hovered ? 1.05f : 1f);
    }

    private static ActionIconCellView findCell(ActionIconCellView[] cellViews, View target) {
        if (cellViews == null || target == null) {
            return null;
        }
        for (ActionIconCellView cellView : cellViews) {
            if (cellView != null && cellView.root == target) {
                return cellView;
            }
        }
        return null;
    }

    private static int indexOfAction(String[] actions, String targetAction) {
        if (actions == null || targetAction == null || targetAction.trim().isEmpty()) {
            return -1;
        }
        for (int index = 0; index < actions.length; index++) {
            if (targetAction.equals(actions[index])) {
                return index;
            }
        }
        return -1;
    }

    private static void applySpecs(
            ClockDetailActionSpec[] destination,
            ClockDetailActionSpec[] source) {
        if (destination == null || source == null) {
            return;
        }
        int count = Math.min(destination.length, source.length);
        for (int slot = 0; slot < count; slot++) {
            ClockDetailActionSpec spec = source[slot];
            destination[slot] = spec != null ? spec : ClockDetailActionSpec.empty(slot);
        }
        for (int slot = count; slot < destination.length; slot++) {
            destination[slot] = ClockDetailActionSpec.empty(slot);
        }
    }

    private static final class ActionIconCellView {
        final LinearLayout root;
        final ImageView iconView;

        ActionIconCellView(LinearLayout root, ImageView iconView) {
            this.root = root;
            this.iconView = iconView;
        }
    }
}
