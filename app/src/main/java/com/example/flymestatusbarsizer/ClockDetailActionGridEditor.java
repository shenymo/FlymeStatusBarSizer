package com.example.flymestatusbarsizer;

import com.example.flymestatusbarsizer.feature.clock.ClockDetailActionCodec;
import com.example.flymestatusbarsizer.feature.clock.ClockDetailActionEntry;
import com.example.flymestatusbarsizer.feature.clock.ClockDetailActionResolver;
import com.example.flymestatusbarsizer.feature.clock.ClockDetailActionSpec;
import com.example.flymestatusbarsizer.feature.clock.ClockDetailAssistantActionCatalog;

import android.app.AlertDialog;
import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

final class ClockDetailActionGridEditor {
    private static final String[] SLOT_LABELS = new String[]{
            "第 1 列",
            "第 2 列",
            "第 3 列",
            "第 4 列"
    };

    private final MainActivity activity;

    ClockDetailActionGridEditor(MainActivity activity) {
        this.activity = activity;
    }

    void show() {
        final ClockDetailActionSpec[] workingSpecs = ClockDetailAssistantActionCatalog.normalizePresetGrid(
                ClockDetailActionCodec.decode(activity.readStringSetting(
                        SettingsStore.KEY_CLOCK_DETAIL_ACTION_GRID_ITEMS_JSON,
                        SettingsStore.DEFAULT_CLOCK_DETAIL_ACTION_GRID_ITEMS_JSON)));
        final TextView[] previewCells = new TextView[ClockDetailActionSpec.SLOT_COUNT];
        final SlotCardViews[] slotCards = new SlotCardViews[ClockDetailActionSpec.SLOT_COUNT];

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

        content.addView(buildPresetCard(workingSpecs, previewCells, slotCards), activity.matchWrap());
        content.addView(buildPreviewCard(previewCells), activity.matchWrapWithTop(12));
        for (int slot = 0; slot < ClockDetailActionSpec.SLOT_COUNT; slot++) {
            content.addView(
                    buildSlotCard(workingSpecs, previewCells, slot, slotCards),
                    activity.matchWrapWithTop(12));
        }

        refreshViews(workingSpecs, previewCells, slotCards);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("调整时间弹窗一行四列图标入口")
                .setMessage("这里只保留四个 Assistant 预设动作。调整顺序后，会按从左到右的顺序显示。")
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
            TextView[] previewCells,
            SlotCardViews[] slotCards) {
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
            refreshViews(workingSpecs, previewCells, slotCards);
            activity.showToast("已恢复默认顺序");
        }), chipButtonLayoutParams(false));
        card.addView(actions, activity.matchWrapWithTop(12));
        return card;
    }

    private View buildPreviewCard(TextView[] previewCells) {
        LinearLayout card = activity.card(activity.featureSurfaceColor(), activity.featureStrokeColor(), 24);
        activity.addProfileSectionHeader(
                card,
                "一行四列预览",
                "顺序按从左到右读取。这里只负责排列，不再支持自定义类型和自定义目标。");
        card.addView(buildPreviewGrid(previewCells), activity.matchWrapWithTop(12));
        return card;
    }

    private View buildPreviewGrid(TextView[] previewCells) {
        LinearLayout grid = new LinearLayout(activity);
        grid.setOrientation(LinearLayout.HORIZONTAL);
        grid.setGravity(Gravity.CENTER_VERTICAL);
        for (int slot = 0; slot < ClockDetailActionSpec.SLOT_COUNT; slot++) {
            TextView cell = buildPreviewCell();
            previewCells[slot] = cell;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f);
            if (slot > 0) {
                params.leftMargin = activity.dp(10);
            }
            grid.addView(cell, params);
        }
        return grid;
    }

    private TextView buildPreviewCell() {
        TextView cell = new TextView(activity);
        cell.setGravity(Gravity.CENTER);
        cell.setTextSize(14);
        cell.setMinHeight(activity.dp(48));
        cell.setPadding(activity.dp(10), activity.dp(12), activity.dp(10), activity.dp(12));
        cell.setSingleLine(true);
        return cell;
    }

    private View buildSlotCard(
            ClockDetailActionSpec[] workingSpecs,
            TextView[] previewCells,
            int slot,
            SlotCardViews[] slotCards) {
        LinearLayout card = activity.card(activity.surfaceSoftColor(), activity.strokeColor(), 22);

        TextView slotTitle = new TextView(activity);
        slotTitle.setText("槽位 " + (slot + 1) + " · " + SLOT_LABELS[slot]);
        slotTitle.setTextColor(activity.textColor());
        slotTitle.setTextSize(16);
        card.addView(slotTitle, activity.matchWrap());

        TextView summaryView = new TextView(activity);
        summaryView.setTextColor(activity.textColor());
        summaryView.setTextSize(18);
        summaryView.setPadding(0, activity.dp(12), 0, 0);
        summaryView.setSingleLine(true);
        card.addView(summaryView, activity.matchWrap());

        TextView metaView = new TextView(activity);
        metaView.setTextColor(activity.subtextColor());
        metaView.setTextSize(12);
        metaView.setPadding(0, activity.dp(6), 0, 0);
        card.addView(metaView, activity.matchWrap());

        TextView targetView = new TextView(activity);
        targetView.setTextColor(activity.primaryColor());
        targetView.setTextSize(13);
        targetView.setPadding(0, activity.dp(10), 0, 0);
        targetView.setLineSpacing(0f, 1.06f);
        card.addView(targetView, activity.matchWrap());

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, activity.dp(14), 0, 0);
        actions.addView(buildChipButton("上移", v -> {
            activity.performTapHaptic(v);
            if (!moveSpec(workingSpecs, slot, slot - 1)) {
                activity.showToast("已经在最前面了");
                return;
            }
            refreshViews(workingSpecs, previewCells, slotCards);
        }), chipButtonLayoutParams(false));
        actions.addView(buildChipButton("下移", v -> {
            activity.performTapHaptic(v);
            if (!moveSpec(workingSpecs, slot, slot + 1)) {
                activity.showToast("已经在最后面了");
                return;
            }
            refreshViews(workingSpecs, previewCells, slotCards);
        }), chipButtonLayoutParams(true));
        actions.addView(buildChipButton("测试启动", v -> {
            activity.performTapHaptic(v);
            testLaunch(workingSpecs[slot]);
        }), chipButtonLayoutParams(true));
        card.addView(actions, activity.matchWrap());

        slotCards[slot] = new SlotCardViews(summaryView, metaView, targetView);
        return card;
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
            TextView[] previewCells,
            SlotCardViews[] slotCards) {
        ClockDetailActionEntry[] entries = ClockDetailActionResolver.resolveEntries(activity, specs);
        for (int slot = 0; slot < ClockDetailActionSpec.SLOT_COUNT; slot++) {
            ClockDetailActionSpec spec = specs[slot];
            ClockDetailActionEntry entry = entries[slot];
            updatePreviewCell(previewCells[slot], spec, entry);
            if (slotCards != null && slot < slotCards.length && slotCards[slot] != null) {
                updateSlotCard(slotCards[slot], spec, entry);
            }
        }
    }

    private void updatePreviewCell(
            TextView cell,
            ClockDetailActionSpec spec,
            ClockDetailActionEntry entry) {
        if (cell == null) {
            return;
        }
        boolean valid = entry != null && entry.valid;
        cell.setText(resolveActionTitle(spec, entry));
        cell.setTextColor(valid ? activity.textColor() : activity.tertiaryColor());
        cell.setBackground(activity.outlinedRect(
                valid ? activity.surfaceColor() : activity.surfaceStrongColor(),
                valid ? activity.featureStrokeColor() : activity.strokeColor(),
                1,
                16));
        cell.setAlpha(valid ? 1f : 0.82f);
    }

    private void updateSlotCard(
            SlotCardViews cardViews,
            ClockDetailActionSpec spec,
            ClockDetailActionEntry entry) {
        if (cardViews == null) {
            return;
        }
        boolean valid = entry != null && entry.valid;
        cardViews.summaryView.setText(resolveActionTitle(spec, entry));
        cardViews.metaView.setText(valid
                ? "当前设备已解析到可启动目标。"
                : "当前设备上还没有解析到可启动目标。");
        cardViews.targetView.setText("动作语义：" + spec.assistantAction);
        cardViews.targetView.setTextColor(valid ? activity.primaryColor() : activity.tertiaryColor());
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

    private boolean moveSpec(ClockDetailActionSpec[] specs, int from, int to) {
        if (specs == null || from < 0 || to < 0
                || from >= specs.length || to >= specs.length || from == to) {
            return false;
        }
        String[] actions = new String[ClockDetailActionSpec.SLOT_COUNT];
        for (int slot = 0; slot < actions.length; slot++) {
            ClockDetailActionSpec spec = slot < specs.length ? specs[slot] : null;
            actions[slot] = spec == null ? "" : spec.assistantAction;
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

    private void testLaunch(ClockDetailActionSpec spec) {
        ClockDetailActionEntry entry = ClockDetailActionResolver.resolveEntry(activity, spec);
        if (entry == null || !entry.valid || entry.launchIntent == null) {
            activity.showToast("当前槽位目标不可启动");
            return;
        }
        try {
            activity.startActivity(new Intent(entry.launchIntent));
            activity.showToast("测试启动已发送");
        } catch (Throwable t) {
            activity.showToast("测试启动失败：" + resolveErrorMessage(t));
        }
    }

    private String resolveErrorMessage(Throwable throwable) {
        if (throwable == null) {
            return "未知错误";
        }
        String message = throwable.getMessage();
        return message == null || message.trim().isEmpty()
                ? throwable.getClass().getSimpleName()
                : message.trim();
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

    private static final class SlotCardViews {
        final TextView summaryView;
        final TextView metaView;
        final TextView targetView;

        SlotCardViews(
                TextView summaryView,
                TextView metaView,
                TextView targetView) {
            this.summaryView = summaryView;
            this.metaView = metaView;
            this.targetView = targetView;
        }
    }
}
