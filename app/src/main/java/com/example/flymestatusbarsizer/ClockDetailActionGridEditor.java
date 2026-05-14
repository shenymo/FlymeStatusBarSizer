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
import android.text.TextUtils;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

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
        final String cachedActionCacheJson = activity.readStringSetting(
                SettingsStore.KEY_CLOCK_DETAIL_ASSISTANT_ACTION_CACHE_JSON,
                SettingsStore.DEFAULT_CLOCK_DETAIL_ASSISTANT_ACTION_CACHE_JSON);
        final String[] availableActions = ClockDetailAssistantActionCatalog.availableActionValues(
                activity,
                cachedActionCacheJson);
        final SelectedCellViews[] selectedCellViews = new SelectedCellViews[ClockDetailActionSpec.SLOT_COUNT];
        final LinkedHashMap<String, CandidateRowViews> candidateRows = new LinkedHashMap<>();
        final TextView selectedSummaryView = new TextView(activity);

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

        content.addView(
                buildSelectedCard(
                        workingSpecs,
                        selectedCellViews,
                        selectedSummaryView,
                        candidateRows),
                activity.matchWrap());
        content.addView(
                buildCandidateCard(
                        workingSpecs,
                        availableActions,
                        selectedCellViews,
                        selectedSummaryView,
                        candidateRows),
                activity.matchWrapWithTop(12));

        refreshViews(workingSpecs, selectedCellViews, selectedSummaryView, candidateRows);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("调整时间弹窗图标入口")
                .setMessage("候选项优先读取本地缓存；点“扫描/刷新”可重新读取 Aicy纵览中的快捷启动列表。最多选择 5 个，长按已选图标拖动排序。")
                .setView(scrollView)
                .setNegativeButton("取消", null)
                .show();
        activity.attachDialogButtonHaptics(dialog);
    }

    private View buildSelectedCard(
            ClockDetailActionSpec[] workingSpecs,
            SelectedCellViews[] selectedCellViews,
            TextView selectedSummaryView,
            Map<String, CandidateRowViews> candidateRows) {
        LinearLayout card = activity.card(activity.surfaceSoftColor(), activity.strokeColor(), 24);
        activity.addProfileSectionHeader(
                card,
                "已选入口",
                "点图标可移除；长按已选图标拖动排序；恢复默认会按 Aicy纵览的默认优先级重建前 5 项。");

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.addView(buildChipButton("恢复默认", v -> {
            activity.performTapHaptic(v);
            applySpecs(workingSpecs, ClockDetailAssistantActionCatalog.createAssistantPresetGrid());
            refreshViews(workingSpecs, selectedCellViews, selectedSummaryView, candidateRows);
            activity.showToast("已恢复默认入口顺序");
        }), chipButtonLayoutParams(false));
        actions.addView(buildChipButton("保存配置", v -> {
            activity.performTapHaptic(v);
            saveWorkingSpecs(workingSpecs);
        }), chipButtonLayoutParams(true));
        card.addView(actions, activity.matchWrapWithTop(12));

        card.addView(
                buildSelectedGrid(workingSpecs, selectedCellViews, selectedSummaryView, candidateRows),
                activity.matchWrapWithTop(12));

        selectedSummaryView.setTextColor(activity.subtextColor());
        selectedSummaryView.setTextSize(12);
        selectedSummaryView.setLineSpacing(0f, 1.08f);
        selectedSummaryView.setPadding(0, activity.dp(12), 0, 0);
        card.addView(selectedSummaryView, activity.matchWrap());
        return card;
    }

    private View buildSelectedGrid(
            ClockDetailActionSpec[] workingSpecs,
            SelectedCellViews[] selectedCellViews,
            TextView selectedSummaryView,
            Map<String, CandidateRowViews> candidateRows) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (int slot = 0; slot < ClockDetailActionSpec.SLOT_COUNT; slot++) {
            final int slotIndex = slot;
            SelectedCellViews cellViews = buildSelectedCell();
            cellViews.root.setOnClickListener(v -> {
                String action = actionAt(workingSpecs, slotIndex);
                if (action.isEmpty()) {
                    return;
                }
                activity.performTapHaptic(v);
                removeAction(workingSpecs, action);
                refreshViews(workingSpecs, selectedCellViews, selectedSummaryView, candidateRows);
            });
            cellViews.root.setOnLongClickListener(this::startSelectedDrag);
            cellViews.root.setOnDragListener((target, event) ->
                    handleSelectedDrag(
                            workingSpecs,
                            selectedCellViews,
                            selectedSummaryView,
                            candidateRows,
                            slotIndex,
                            target,
                            event));
            selectedCellViews[slot] = cellViews;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f);
            if (slot > 0) {
                params.leftMargin = activity.dp(6);
            }
            row.addView(cellViews.root, params);
        }
        return row;
    }

    private SelectedCellViews buildSelectedCell() {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setMinimumHeight(activity.dp(76));
        root.setPadding(activity.dp(6), activity.dp(8), activity.dp(6), activity.dp(8));

        ImageView iconView = new ImageView(activity);
        int iconSize = activity.dp(30);
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        styleRoundedIcon(iconView, 10);
        root.addView(iconView, new LinearLayout.LayoutParams(iconSize, iconSize));

        TextView titleView = new TextView(activity);
        titleView.setIncludeFontPadding(false);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setGravity(Gravity.CENTER);
        titleView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        titleView.setTextSize(11);
        titleView.setPadding(0, activity.dp(6), 0, 0);
        root.addView(titleView, activity.matchWrap());
        return new SelectedCellViews(root, iconView, titleView);
    }

    private View buildCandidateCard(
            ClockDetailActionSpec[] workingSpecs,
            String[] availableActions,
            SelectedCellViews[] selectedCellViews,
            TextView selectedSummaryView,
            LinkedHashMap<String, CandidateRowViews> candidateRows) {
        LinearLayout card = activity.card(activity.featureSurfaceColor(), activity.featureStrokeColor(), 24);
        activity.addProfileSectionHeader(
                card,
                "可选快捷启动",
                "列表优先读取已缓存结果；点“扫描/刷新”重新读取 Aicy纵览中的快捷启动列表。");
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.addView(buildChipButton("扫描/刷新", v -> {
            activity.performTapHaptic(v);
            rescanAssistantActions(
                    v,
                    workingSpecs,
                    selectedCellViews,
                    selectedSummaryView,
                    candidateRows,
                    list);
        }), chipButtonLayoutParams(false));
        card.addView(actions, activity.matchWrapWithTop(12));
        populateCandidateList(
                list,
                availableActions,
                workingSpecs,
                selectedCellViews,
                selectedSummaryView,
                candidateRows);
        card.addView(list, activity.matchWrapWithTop(12));
        return card;
    }

    private void populateCandidateList(
            LinearLayout list,
            String[] availableActions,
            ClockDetailActionSpec[] workingSpecs,
            SelectedCellViews[] selectedCellViews,
            TextView selectedSummaryView,
            LinkedHashMap<String, CandidateRowViews> candidateRows) {
        if (list == null) {
            return;
        }
        list.removeAllViews();
        if (candidateRows != null) {
            candidateRows.clear();
        }
        if (availableActions != null) {
            int visibleIndex = 0;
            for (String value : availableActions) {
                String action = ClockDetailAssistantActionCatalog.normalizeAction(value);
                if (action.isEmpty() || (candidateRows != null && candidateRows.containsKey(action))) {
                    continue;
                }
                CandidateRowViews rowViews = buildCandidateRow(
                        action,
                        workingSpecs,
                        selectedCellViews,
                        selectedSummaryView,
                        candidateRows);
                if (candidateRows != null) {
                    candidateRows.put(action, rowViews);
                }
                LinearLayout.LayoutParams params = activity.matchWrap();
                if (visibleIndex > 0) {
                    params.topMargin = activity.dp(8);
                }
                list.addView(rowViews.root, params);
                visibleIndex++;
            }
        }
        if (candidateRows == null || candidateRows.isEmpty()) {
            list.addView(buildCandidateEmptyView(), activity.matchWrap());
        }
    }

    private TextView buildCandidateEmptyView() {
        TextView emptyView = new TextView(activity);
        emptyView.setText("当前没有缓存到 Aicy纵览中的快捷启动，请点“扫描/刷新”。");
        emptyView.setTextColor(activity.subtextColor());
        emptyView.setTextSize(13);
        return emptyView;
    }

    private void rescanAssistantActions(
            View triggerView,
            ClockDetailActionSpec[] workingSpecs,
            SelectedCellViews[] selectedCellViews,
            TextView selectedSummaryView,
            LinkedHashMap<String, CandidateRowViews> candidateRows,
            LinearLayout candidateList) {
        if (triggerView != null) {
            triggerView.setEnabled(false);
            triggerView.setAlpha(0.58f);
        }
        activity.showToast("正在扫描Aicy纵览中的快捷启动...");
        new Thread(() -> {
            String cacheJson = ClockDetailAssistantActionCatalog.buildAvailableActionCacheJson(activity);
            String[] availableActions = ClockDetailAssistantActionCatalog.actionValuesFromCacheJson(cacheJson);
            activity.runOnUiThread(() -> {
                if (triggerView != null) {
                    triggerView.setEnabled(true);
                    triggerView.setAlpha(1f);
                }
                if (availableActions.length == 0 || cacheJson.isEmpty()) {
                    refreshViews(workingSpecs, selectedCellViews, selectedSummaryView, candidateRows);
                    activity.showToast("没有扫描到 Aicy纵览中的快捷启动");
                    return;
                }
                activity.putStringSetting(
                        SettingsStore.KEY_CLOCK_DETAIL_ASSISTANT_ACTION_CACHE_JSON,
                        cacheJson);
                populateCandidateList(
                        candidateList,
                        availableActions,
                        workingSpecs,
                        selectedCellViews,
                        selectedSummaryView,
                        candidateRows);
                refreshViews(workingSpecs, selectedCellViews, selectedSummaryView, candidateRows);
                activity.showToast("已缓存 " + availableActions.length + " 个快捷启动");
            });
        }, "clock-detail-assistant-scan").start();
    }

    private CandidateRowViews buildCandidateRow(
            String action,
            ClockDetailActionSpec[] workingSpecs,
            SelectedCellViews[] selectedCellViews,
            TextView selectedSummaryView,
            LinkedHashMap<String, CandidateRowViews> candidateRows) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(activity.dp(12), activity.dp(12), activity.dp(12), activity.dp(12));

        ImageView iconView = new ImageView(activity);
        int iconSize = activity.dp(28);
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        styleRoundedIcon(iconView, 10);
        row.addView(iconView, new LinearLayout.LayoutParams(iconSize, iconSize));

        LinearLayout textColumn = new LinearLayout(activity);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.setPadding(activity.dp(12), 0, activity.dp(12), 0);

        TextView titleView = new TextView(activity);
        titleView.setTextColor(activity.textColor());
        titleView.setTextSize(15);
        textColumn.addView(titleView, activity.matchWrap());

        TextView subtitleView = new TextView(activity);
        subtitleView.setTextColor(activity.subtextColor());
        subtitleView.setTextSize(12);
        subtitleView.setPadding(0, activity.dp(4), 0, 0);
        textColumn.addView(subtitleView, activity.matchWrap());
        row.addView(textColumn, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f));

        TextView stateChip = activity.chip("添加", activity.surfaceColor(), activity.primaryColor());
        row.addView(stateChip, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        Runnable toggleAction = () -> {
            activity.performTapHaptic(row);
            if (isSelected(workingSpecs, action)) {
                removeAction(workingSpecs, action);
                refreshViews(workingSpecs, selectedCellViews, selectedSummaryView, candidateRows);
                return;
            }
            if (selectedCount(workingSpecs) >= ClockDetailActionSpec.SLOT_COUNT) {
                activity.showToast("最多只能选择 5 个图标入口");
                return;
            }
            addAction(workingSpecs, action);
            refreshViews(workingSpecs, selectedCellViews, selectedSummaryView, candidateRows);
        };
        activity.setTapClickListener(row, v -> toggleAction.run());
        activity.setTapClickListener(stateChip, v -> toggleAction.run());
        return new CandidateRowViews(row, iconView, titleView, subtitleView, stateChip, action);
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

    private void saveWorkingSpecs(ClockDetailActionSpec[] workingSpecs) {
        ClockDetailActionSpec[] normalized =
                ClockDetailAssistantActionCatalog.normalizePresetGrid(workingSpecs);
        activity.putStringSetting(
                SettingsStore.KEY_CLOCK_DETAIL_ACTION_GRID_ITEMS_JSON,
                ClockDetailActionCodec.encode(normalized));
        activity.showToast("时间弹窗图标入口已保存");
    }

    private void refreshViews(
            ClockDetailActionSpec[] workingSpecs,
            SelectedCellViews[] selectedCellViews,
            TextView selectedSummaryView,
            Map<String, CandidateRowViews> candidateRows) {
        ClockDetailActionEntry[] selectedEntries = ClockDetailActionResolver.resolveEntries(activity, workingSpecs);
        int selectedCount = 0;
        for (int slot = 0; slot < ClockDetailActionSpec.SLOT_COUNT; slot++) {
            ClockDetailActionSpec spec = specAt(workingSpecs, slot);
            ClockDetailActionEntry entry = selectedEntries[slot];
            if (!spec.assistantAction.isEmpty()) {
                selectedCount++;
            }
            updateSelectedCell(selectedCellViews[slot], spec, entry);
        }
        if (selectedSummaryView != null) {
            StringBuilder summary = new StringBuilder();
            summary.append("已选 ").append(selectedCount).append(" / ").append(ClockDetailActionSpec.SLOT_COUNT);
            summary.append("。点图标移除，长按拖动排序。");
            selectedSummaryView.setText(summary);
        }
        if (candidateRows != null) {
            for (Map.Entry<String, CandidateRowViews> entry : candidateRows.entrySet()) {
                updateCandidateRow(workingSpecs, entry.getValue());
            }
        }
    }

    private void updateSelectedCell(
            SelectedCellViews cellViews,
            ClockDetailActionSpec spec,
            ClockDetailActionEntry entry) {
        if (cellViews == null) {
            return;
        }
        String action = spec == null ? "" : spec.assistantAction;
        boolean selected = !action.isEmpty();
        cellViews.root.setTag(action);
        cellViews.root.setBackground(activity.outlinedRect(
                selected ? activity.surfaceColor() : activity.surfaceSoftColor(),
                selected ? activity.primaryColor() : activity.strokeColor(),
                1,
                18));
        cellViews.root.setAlpha(selected ? 1f : 0.72f);
        cellViews.root.setScaleX(1f);
        cellViews.root.setScaleY(1f);
        if (!selected) {
            cellViews.iconView.setImageDrawable(null);
            cellViews.iconView.setAlpha(0.32f);
            cellViews.titleView.setText("空位");
            cellViews.titleView.setTextColor(activity.tertiaryColor());
            cellViews.root.setContentDescription("空位");
            return;
        }
        cellViews.iconView.setImageDrawable(entry != null && entry.hasIcon()
                ? entry.icon
                : ClockDetailAssistantActionCatalog.resolveActionIcon(activity, action));
        cellViews.iconView.setAlpha(1f);
        cellViews.titleView.setText(ClockDetailAssistantActionCatalog.resolveActionName(action));
        cellViews.titleView.setTextColor(activity.textColor());
        cellViews.root.setContentDescription(resolveActionTitle(spec, entry));
    }

    private void updateCandidateRow(
            ClockDetailActionSpec[] workingSpecs,
            CandidateRowViews rowViews) {
        if (rowViews == null) {
            return;
        }
        String action = rowViews.action;
        ClockDetailActionSpec spec = ClockDetailAssistantActionCatalog.assistantSpec(
                0,
                action,
                ClockDetailAssistantActionCatalog.resolveActionTitle(action));
        ClockDetailActionEntry entry = ClockDetailActionResolver.resolveEntry(activity, spec);
        boolean selected = isSelected(workingSpecs, action);
        rowViews.root.setBackground(activity.outlinedRect(
                selected ? activity.surfaceColor() : activity.surfaceSoftColor(),
                selected ? activity.primaryColor() : activity.featureStrokeColor(),
                1,
                18));
        rowViews.root.setAlpha(1f);
        rowViews.iconView.setImageDrawable(entry != null && entry.hasIcon()
                ? entry.icon
                : ClockDetailAssistantActionCatalog.resolveActionIcon(activity, action));
        rowViews.iconView.setAlpha(1f);
        rowViews.titleView.setText(ClockDetailAssistantActionCatalog.resolveActionName(action));
        rowViews.subtitleView.setText(buildCandidateSubtitle(action));
        rowViews.stateChip.setText(selected ? "已选" : "添加");
        rowViews.stateChip.setTextColor(selected ? android.graphics.Color.WHITE : activity.primaryColor());
        rowViews.stateChip.setBackground(selected
                ? activity.roundRect(activity.primaryColor(), 99)
                : activity.outlinedRect(
                        activity.surfaceColor(),
                        activity.featureStrokeColor(),
                        1,
                        99));
    }

    private String buildCandidateSubtitle(String action) {
        return ClockDetailAssistantActionCatalog.resolveActionAppName(action);
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
        return ClockDetailAssistantActionCatalog.resolveActionTitle(spec.assistantAction);
    }

    private boolean startSelectedDrag(View view) {
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

    private boolean handleSelectedDrag(
            ClockDetailActionSpec[] workingSpecs,
            SelectedCellViews[] selectedCellViews,
            TextView selectedSummaryView,
            Map<String, CandidateRowViews> candidateRows,
            int targetSlot,
            View target,
            DragEvent event) {
        SelectedCellViews targetCell = findSelectedCell(selectedCellViews, target);
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return event.getLocalState() instanceof View
                        && ((View) event.getLocalState()).getTag() instanceof String;
            case DragEvent.ACTION_DRAG_ENTERED:
                applySelectedHoverState(targetCell, true);
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
                applySelectedHoverState(targetCell, false);
                return true;
            case DragEvent.ACTION_DROP:
                applySelectedHoverState(targetCell, false);
                Object localState = event.getLocalState();
                if (!(localState instanceof View) || !(((View) localState).getTag() instanceof String)) {
                    return false;
                }
                if (moveAction(
                        workingSpecs,
                        (String) ((View) localState).getTag(),
                        targetSlot)) {
                    refreshViews(workingSpecs, selectedCellViews, selectedSummaryView, candidateRows);
                }
                return true;
            case DragEvent.ACTION_DRAG_ENDED:
                applySelectedHoverState(targetCell, false);
                Object draggedView = event.getLocalState();
                if (draggedView instanceof View) {
                    ((View) draggedView).setAlpha(1f);
                }
                refreshViews(workingSpecs, selectedCellViews, selectedSummaryView, candidateRows);
                return true;
            default:
                return true;
        }
    }

    private void applySelectedHoverState(SelectedCellViews cellViews, boolean hovered) {
        if (cellViews == null) {
            return;
        }
        cellViews.root.setScaleX(hovered ? 1.04f : 1f);
        cellViews.root.setScaleY(hovered ? 1.04f : 1f);
    }

    private SelectedCellViews findSelectedCell(SelectedCellViews[] selectedCellViews, View target) {
        if (selectedCellViews == null || target == null) {
            return null;
        }
        for (SelectedCellViews cellViews : selectedCellViews) {
            if (cellViews != null && cellViews.root == target) {
                return cellViews;
            }
        }
        return null;
    }

    private boolean isSelected(ClockDetailActionSpec[] specs, String action) {
        return findSelectedIndex(specs, action) >= 0;
    }

    private int findSelectedIndex(ClockDetailActionSpec[] specs, String action) {
        String normalizedAction = ClockDetailAssistantActionCatalog.normalizeAction(action);
        if (normalizedAction.isEmpty()) {
            return -1;
        }
        for (int slot = 0; slot < ClockDetailActionSpec.SLOT_COUNT; slot++) {
            if (normalizedAction.equals(actionAt(specs, slot))) {
                return slot;
            }
        }
        return -1;
    }

    private int selectedCount(ClockDetailActionSpec[] specs) {
        int count = 0;
        for (int slot = 0; slot < ClockDetailActionSpec.SLOT_COUNT; slot++) {
            if (!actionAt(specs, slot).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private void addAction(ClockDetailActionSpec[] specs, String action) {
        ArrayList<String> selectedActions = collectSelectedActions(specs);
        String normalizedAction = ClockDetailAssistantActionCatalog.normalizeAction(action);
        if (normalizedAction.isEmpty() || selectedActions.contains(normalizedAction)) {
            return;
        }
        selectedActions.add(normalizedAction);
        rebuildSelectedSpecs(specs, selectedActions);
    }

    private void removeAction(ClockDetailActionSpec[] specs, String action) {
        ArrayList<String> selectedActions = collectSelectedActions(specs);
        selectedActions.remove(ClockDetailAssistantActionCatalog.normalizeAction(action));
        rebuildSelectedSpecs(specs, selectedActions);
    }

    private boolean moveAction(ClockDetailActionSpec[] specs, String action, int targetSlot) {
        ArrayList<String> selectedActions = collectSelectedActions(specs);
        String normalizedAction = ClockDetailAssistantActionCatalog.normalizeAction(action);
        int fromIndex = selectedActions.indexOf(normalizedAction);
        if (fromIndex < 0) {
            return false;
        }
        selectedActions.remove(fromIndex);
        int insertIndex = Math.max(0, Math.min(targetSlot, selectedActions.size()));
        selectedActions.add(insertIndex, normalizedAction);
        rebuildSelectedSpecs(specs, selectedActions);
        return true;
    }

    private ArrayList<String> collectSelectedActions(ClockDetailActionSpec[] specs) {
        ArrayList<String> actions = new ArrayList<>();
        if (specs == null) {
            return actions;
        }
        for (int slot = 0; slot < ClockDetailActionSpec.SLOT_COUNT; slot++) {
            String action = actionAt(specs, slot);
            if (!action.isEmpty() && !actions.contains(action)) {
                actions.add(action);
            }
        }
        return actions;
    }

    private void rebuildSelectedSpecs(ClockDetailActionSpec[] specs, ArrayList<String> selectedActions) {
        if (specs == null) {
            return;
        }
        for (int slot = 0; slot < ClockDetailActionSpec.SLOT_COUNT; slot++) {
            if (selectedActions != null && slot < selectedActions.size()) {
                String action = selectedActions.get(slot);
                specs[slot] = ClockDetailAssistantActionCatalog.assistantSpec(
                        slot,
                        action,
                        ClockDetailAssistantActionCatalog.resolveActionTitle(action));
            } else {
                specs[slot] = ClockDetailActionSpec.empty(slot);
            }
        }
    }

    private String actionAt(ClockDetailActionSpec[] specs, int slot) {
        if (specs == null || slot < 0 || slot >= specs.length) {
            return "";
        }
        ClockDetailActionSpec spec = specs[slot];
        return spec == null ? "" : ClockDetailAssistantActionCatalog.normalizeAction(spec.assistantAction);
    }

    private ClockDetailActionSpec specAt(ClockDetailActionSpec[] specs, int slot) {
        if (specs == null || slot < 0 || slot >= specs.length) {
            return ClockDetailActionSpec.empty(slot);
        }
        ClockDetailActionSpec spec = specs[slot];
        return spec != null ? spec : ClockDetailActionSpec.empty(slot);
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

    private static final class SelectedCellViews {
        final LinearLayout root;
        final ImageView iconView;
        final TextView titleView;

        SelectedCellViews(LinearLayout root, ImageView iconView, TextView titleView) {
            this.root = root;
            this.iconView = iconView;
            this.titleView = titleView;
        }
    }

    private static final class CandidateRowViews {
        final LinearLayout root;
        final ImageView iconView;
        final TextView titleView;
        final TextView subtitleView;
        final TextView stateChip;
        final String action;

        CandidateRowViews(
                LinearLayout root,
                ImageView iconView,
                TextView titleView,
                TextView subtitleView,
                TextView stateChip,
                String action) {
            this.root = root;
            this.iconView = iconView;
            this.titleView = titleView;
            this.subtitleView = subtitleView;
            this.stateChip = stateChip;
            this.action = action;
        }
    }
}
