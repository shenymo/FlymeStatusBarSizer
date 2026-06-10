package com.example.flymestatusbarsizer;

import android.content.ClipData;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.TextUtils;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.flymestatusbarsizer.feature.ime.ImeToolbarSpec;

import java.util.ArrayList;

final class ImeToolbarEditor {
    private final MainActivity activity;
    private final ArrayList<String> buttonSlots = new ArrayList<>();

    private LinearLayout buttonContainer;
    private LinearLayout buttonPoolContainer;
    private LinearLayout buttonSlotContainer;
    private TextView applyButton;
    private TextView applyStateView;
    private String appliedSlotsSerialized = SettingsStore.DEFAULT_IME_CONTROL_BAR_BUTTON_SLOTS;

    ImeToolbarEditor(MainActivity activity) {
        this.activity = activity;
    }

    LinearLayout buildSettingsContent() {
        LinearLayout details = new LinearLayout(activity);
        details.setOrientation(LinearLayout.VERTICAL);

        activity.addSwitchRow(details, "替换原生输入法控制栏",
                "打开后统一接管输入法控制栏：替换当前控制栏、去掉深灰背景、同步输入法背景，并把工具按钮合并进控制栏。",
                SettingsStore.KEY_IME_REPLACE_ORIGINAL_CONTROL_BAR,
                SettingsStore.DEFAULT_IME_REPLACE_ORIGINAL_CONTROL_BAR);
        activity.addDivider(details);
        activity.addApplySliderRow(details, "输入法图标大小",
                "调节底部 7 格里图标的占位比例。数值越大，图标越大，也更接近铺满整条控制栏。",
                SettingsStore.KEY_IME_CONTROL_BAR_ICON_SCALE_PERCENT,
                SettingsStore.DEFAULT_IME_CONTROL_BAR_ICON_SCALE_PERCENT,
                60, 180, "%");
        activity.addDivider(details);
        activity.addApplySliderRow(details, "输入法图标透明度",
                "调节输入法控制栏图标透明度。100% 完全不透明，数值越小越淡。",
                SettingsStore.KEY_IME_CONTROL_BAR_ICON_ALPHA_PERCENT,
                SettingsStore.DEFAULT_IME_CONTROL_BAR_ICON_ALPHA_PERCENT,
                10, 100, "%");
        activity.addDivider(details);
        activity.addProfileSectionHeader(details, "按钮位置与显隐",
                "长按按钮拖到下面固定槽位里就会显示；拖回按钮池就会隐藏。从左到右就是输入法控制栏里的实际位置；拖拽后先保存在页面草稿里，点击应用才会写入配置并刷新当前输入法界面。");

        buttonContainer = new LinearLayout(activity);
        buttonContainer.setOrientation(LinearLayout.VERTICAL);
        buttonContainer.setPadding(0, activity.dp(12), 0, 0);
        details.addView(buttonContainer, activity.matchWrap());
        loadConfig();
        renderEditor();
        return details;
    }

    private void loadConfig() {
        appliedSlotsSerialized = activity.readStringSetting(
                SettingsStore.KEY_IME_CONTROL_BAR_BUTTON_SLOTS,
                SettingsStore.DEFAULT_IME_CONTROL_BAR_BUTTON_SLOTS);
        buttonSlots.clear();
        buttonSlots.addAll(ImeToolbarSpec.resolveButtonSlots(appliedSlotsSerialized));
    }

    private void renderEditor() {
        if (buttonContainer == null) {
            return;
        }
        buttonContainer.removeAllViews();
        activity.addProfileSectionHeader(buttonContainer, "按钮池",
                "长按这里的按钮拖到下方槽位就会显示；把下方按钮拖回这里就会隐藏。");

        buttonPoolContainer = new LinearLayout(activity);
        buttonPoolContainer.setOrientation(LinearLayout.VERTICAL);
        buttonPoolContainer.setPadding(activity.dp(12), activity.dp(12), activity.dp(12), activity.dp(12));
        buttonPoolContainer.setBackground(buildPoolBackground(false));
        buttonPoolContainer.setOnDragListener(this::handlePoolDrag);
        buttonContainer.addView(buttonPoolContainer, activity.matchWrapWithTop(10));

        activity.addProfileSectionHeader(buttonContainer, "底部 7 个固定槽位",
                "从左到右对应输入法控制栏的实际位置；空槽位会保留占位，验证码占 2 格。");

        buttonSlotContainer = new LinearLayout(activity);
        buttonSlotContainer.setOrientation(LinearLayout.HORIZONTAL);
        buttonSlotContainer.setPadding(0, activity.dp(10), 0, 0);
        buttonContainer.addView(buttonSlotContainer, activity.matchWrap());

        LinearLayout actionRow = new LinearLayout(activity);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        actionRow.setPadding(0, activity.dp(14), 0, 0);

        applyStateView = new TextView(activity);
        applyStateView.setTextColor(activity.subtextColor());
        applyStateView.setTextSize(12);
        actionRow.addView(applyStateView, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        applyButton = activity.filledButton("应用", activity.primaryColor(), android.graphics.Color.WHITE);
        activity.setTapClickListener(applyButton, v -> applyConfig());
        actionRow.addView(applyButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        buttonContainer.addView(actionRow, activity.matchWrap());
        syncEditor();
    }

    private void syncEditor() {
        renderPool();
        renderSlots();
        syncApplyState();
    }

    private void renderPool() {
        if (buttonPoolContainer == null) {
            return;
        }
        buttonPoolContainer.removeAllViews();
        ArrayList<String> poolButtons = ImeToolbarSpec.getAllButtons();
        for (String slotButton : buttonSlots) {
            if (!TextUtils.isEmpty(slotButton)) {
                poolButtons.remove(slotButton);
            }
        }
        if (poolButtons.isEmpty()) {
            TextView empty = new TextView(activity);
            empty.setText("所有按钮都已经放进下方槽位。");
            empty.setTextColor(activity.subtextColor());
            empty.setTextSize(13);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, activity.dp(12), 0, activity.dp(12));
            buttonPoolContainer.addView(empty, activity.matchWrap());
            return;
        }
        for (int i = 0; i < poolButtons.size(); i += MainActivity.IME_CONTROL_BAR_POOL_ROW_ITEM_COUNT) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            if (i > 0) {
                row.setPadding(0, activity.dp(8), 0, 0);
            }
            for (int j = 0; j < MainActivity.IME_CONTROL_BAR_POOL_ROW_ITEM_COUNT; j++) {
                int index = i + j;
                if (index >= poolButtons.size()) {
                    View spacer = new View(activity);
                    row.addView(spacer, new LinearLayout.LayoutParams(0, 0, 1f));
                    continue;
                }
                TextView item = buildPoolItem(poolButtons.get(index));
                LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                if (j > 0) {
                    itemLp.leftMargin = activity.dp(8);
                }
                row.addView(item, itemLp);
            }
            buttonPoolContainer.addView(row, activity.matchWrap());
        }
    }

    private TextView buildPoolItem(String button) {
        TextView item = new TextView(activity);
        item.setText(ImeToolbarSpec.getButtonLabel(button));
        item.setTextColor(activity.textColor());
        item.setTextSize(14);
        item.setGravity(Gravity.CENTER);
        item.setMinHeight(activity.dp(48));
        item.setPadding(activity.dp(8), activity.dp(12), activity.dp(8), activity.dp(12));
        item.setBackground(activity.outlinedRect(activity.surfaceColor(), activity.strokeColor(), 1, 18));
        item.setOnLongClickListener(v -> startDrag(v, button, -1));
        return item;
    }

    private void renderSlots() {
        if (buttonSlotContainer == null) {
            return;
        }
        buttonSlotContainer.removeAllViews();
        for (int i = 0; i < ImeToolbarSpec.getButtonSlotCount(); i++) {
            String button = i < buttonSlots.size() ? buttonSlots.get(i) : null;
            int span = ImeToolbarSpec.getButtonSpan(button);
            View slotView = buildSlotView(i, button);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, span);
            if (i > 0) {
                lp.leftMargin = activity.dp(6);
            }
            buttonSlotContainer.addView(slotView, lp);
            if (span > 1) {
                i += span - 1;
            }
        }
    }

    private View buildSlotView(int slotIndex, String button) {
        boolean covered = isCoveredByWideButton(slotIndex);
        LinearLayout slot = new LinearLayout(activity);
        slot.setOrientation(LinearLayout.VERTICAL);
        slot.setGravity(Gravity.CENTER);
        slot.setPadding(activity.dp(4), activity.dp(10), activity.dp(4), activity.dp(10));
        slot.setMinimumHeight(activity.dp(72));
        slot.setTag(Integer.valueOf(slotIndex));
        slot.setBackground(buildSlotBackground(!TextUtils.isEmpty(button) || covered, false));
        slot.setOnDragListener(this::handleSlotDrag);
        if (!TextUtils.isEmpty(button) && !covered) {
            slot.setOnLongClickListener(v -> startDrag(v, button, slotIndex));
        }

        TextView label = new TextView(activity);
        label.setText(covered ? "占用" : (TextUtils.isEmpty(button) ? "空槽" : ImeToolbarSpec.getButtonLabel(button)));
        label.setTextColor((TextUtils.isEmpty(button) && !covered) ? activity.subtextColor() : activity.textColor());
        label.setTextSize((TextUtils.isEmpty(button) && !covered) ? 11 : 13);
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(2);
        slot.addView(label, activity.matchWrap());
        return slot;
    }

    private boolean startDrag(View view, String button, int sourceSlotIndex) {
        if (view == null || TextUtils.isEmpty(button)) {
            return false;
        }
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        ClipData data = ClipData.newPlainText(MainActivity.IME_CONTROL_BAR_DRAG_LABEL, button);
        DragState state = new DragState(view, button, sourceSlotIndex);
        View.DragShadowBuilder shadow = new View.DragShadowBuilder(view);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            view.startDragAndDrop(data, shadow, state, 0);
        } else {
            view.startDrag(data, shadow, state, 0);
        }
        view.setAlpha(0.55f);
        return true;
    }

    private boolean handlePoolDrag(View target, DragEvent event) {
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return event.getLocalState() instanceof DragState;
            case DragEvent.ACTION_DRAG_ENTERED:
                target.setBackground(buildPoolBackground(true));
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
                target.setBackground(buildPoolBackground(false));
                return true;
            case DragEvent.ACTION_DROP:
                target.setBackground(buildPoolBackground(false));
                DragState state = event.getLocalState() instanceof DragState
                        ? (DragState) event.getLocalState() : null;
                if (state == null || state.sourceSlotIndex < 0) {
                    return false;
                }
                removeButtonFromSlots(state.button);
                syncEditor();
                return true;
            case DragEvent.ACTION_DRAG_ENDED:
                target.setBackground(buildPoolBackground(false));
                restoreDraggedView(event.getLocalState());
                return true;
            default:
                return true;
        }
    }

    private boolean handleSlotDrag(View target, DragEvent event) {
        if (!(target.getTag() instanceof Integer)) {
            return false;
        }
        int slotIndex = (Integer) target.getTag();
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return event.getLocalState() instanceof DragState;
            case DragEvent.ACTION_DRAG_ENTERED:
                target.setBackground(buildSlotBackground(isOccupied(slotIndex), true));
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
                target.setBackground(buildSlotBackground(isOccupied(slotIndex), false));
                return true;
            case DragEvent.ACTION_DROP:
                target.setBackground(buildSlotBackground(isOccupied(slotIndex), false));
                DragState state = event.getLocalState() instanceof DragState
                        ? (DragState) event.getLocalState() : null;
                if (state == null) {
                    return false;
                }
                moveButtonToSlot(state.button, slotIndex);
                syncEditor();
                return true;
            case DragEvent.ACTION_DRAG_ENDED:
                target.setBackground(buildSlotBackground(isOccupied(slotIndex), false));
                restoreDraggedView(event.getLocalState());
                return true;
            default:
                return true;
        }
    }

    private void moveButtonToSlot(String button, int targetSlotIndex) {
        if (TextUtils.isEmpty(button) || targetSlotIndex < 0 || targetSlotIndex >= buttonSlots.size()) {
            return;
        }
        int span = ImeToolbarSpec.getButtonSpan(button);
        if (targetSlotIndex + span > buttonSlots.size()) {
            targetSlotIndex = buttonSlots.size() - span;
        }
        int sourceSlotIndex = findButtonSlotIndex(button);
        if (sourceSlotIndex == targetSlotIndex) {
            return;
        }
        if (sourceSlotIndex >= 0) {
            clearButtonSpan(sourceSlotIndex);
        }
        for (int i = 0; i < span; i++) {
            clearSlotOrOwner(targetSlotIndex + i);
        }
        buttonSlots.set(targetSlotIndex, button);
        normalizeButtonSlots();
    }

    private void removeButtonFromSlots(String button) {
        int slotIndex = findButtonSlotIndex(button);
        if (slotIndex >= 0) {
            clearButtonSpan(slotIndex);
            normalizeButtonSlots();
        }
    }

    private int findButtonSlotIndex(String button) {
        if (TextUtils.isEmpty(button)) {
            return -1;
        }
        for (int i = 0; i < buttonSlots.size(); i++) {
            if (button.equals(buttonSlots.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private boolean isOccupied(int slotIndex) {
        return slotIndex < buttonSlots.size()
                && (!TextUtils.isEmpty(buttonSlots.get(slotIndex)) || isCoveredByWideButton(slotIndex));
    }

    private boolean isCoveredByWideButton(int slotIndex) {
        int owner = findWideButtonOwner(slotIndex);
        return owner >= 0 && owner != slotIndex;
    }

    private int findWideButtonOwner(int slotIndex) {
        for (int i = 0; i < buttonSlots.size(); i++) {
            String button = buttonSlots.get(i);
            int span = ImeToolbarSpec.getButtonSpan(button);
            if (!TextUtils.isEmpty(button) && span > 1 && slotIndex >= i && slotIndex < i + span) {
                return i;
            }
        }
        return -1;
    }

    private void clearSlotOrOwner(int slotIndex) {
        int owner = findWideButtonOwner(slotIndex);
        if (owner >= 0) {
            clearButtonSpan(owner);
        } else if (slotIndex >= 0 && slotIndex < buttonSlots.size()) {
            buttonSlots.set(slotIndex, null);
        }
    }

    private void clearButtonSpan(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= buttonSlots.size()) {
            return;
        }
        String button = buttonSlots.get(slotIndex);
        int span = ImeToolbarSpec.getButtonSpan(button);
        for (int i = 0; i < span && slotIndex + i < buttonSlots.size(); i++) {
            buttonSlots.set(slotIndex + i, null);
        }
    }

    private void normalizeButtonSlots() {
        ArrayList<String> normalized =
                ImeToolbarSpec.resolveButtonSlots(ImeToolbarSpec.serializeButtonSlots(buttonSlots));
        buttonSlots.clear();
        buttonSlots.addAll(normalized);
    }

    private android.graphics.drawable.GradientDrawable buildPoolBackground(boolean active) {
        int background = active ? activity.surfaceStrongColor() : activity.surfaceSoftColor();
        int stroke = active ? activity.featureStrokeColor() : activity.strokeColor();
        return activity.outlinedRect(background, stroke, 1, 22);
    }

    private android.graphics.drawable.GradientDrawable buildSlotBackground(boolean occupied, boolean active) {
        int background = active
                ? activity.surfaceStrongColor()
                : (occupied ? activity.featureSurfaceColor() : activity.surfaceColor());
        int stroke = active
                ? activity.featureStrokeColor()
                : (occupied ? activity.primaryContainerColor() : activity.strokeColor());
        return activity.outlinedRect(background, stroke, 1, 18);
    }

    private void restoreDraggedView(Object localState) {
        if (!(localState instanceof DragState)) {
            return;
        }
        View sourceView = ((DragState) localState).sourceView;
        if (sourceView != null) {
            sourceView.setAlpha(1f);
        }
    }

    private boolean hasPendingChanges() {
        return !TextUtils.equals(ImeToolbarSpec.serializeButtonSlots(buttonSlots), appliedSlotsSerialized);
    }

    private void syncApplyState() {
        boolean changed = hasPendingChanges();
        if (applyStateView != null) {
            applyStateView.setText(changed ? "有未应用的改动" : "当前草稿已应用");
            applyStateView.setTextColor(changed ? activity.primaryColor() : activity.subtextColor());
        }
        if (applyButton != null) {
            applyButton.setEnabled(changed);
            applyButton.setAlpha(changed ? 1f : 0.55f);
        }
    }

    private void applyConfig() {
        if (!hasPendingChanges()) {
            activity.showToast("没有新的输入法按钮改动");
            return;
        }
        saveConfig();
        syncApplyState();
        activity.showToast("输入法按钮布局已应用");
    }

    private void saveConfig() {
        appliedSlotsSerialized = ImeToolbarSpec.serializeButtonSlots(buttonSlots);
        SharedPreferences.Editor editor = activity.prefs().edit();
        editor.putString(SettingsStore.KEY_IME_CONTROL_BAR_BUTTON_SLOTS, appliedSlotsSerialized);
        editor.remove(SettingsStore.KEY_IME_CONTROL_BAR_BUTTON_ORDER);
        editor.remove(SettingsStore.KEY_IME_CONTROL_BAR_HIDDEN_BUTTONS);
        editor.remove(SettingsStore.KEY_IME_CONTROL_BAR_ALIGNMENT);
        editor.apply();
        SettingsStore.notifyChanged(activity);
        activity.invalidatePreview();
    }

    private static final class DragState {
        private final View sourceView;
        private final String button;
        private final int sourceSlotIndex;

        private DragState(View sourceView, String button, int sourceSlotIndex) {
            this.sourceView = sourceView;
            this.button = button;
            this.sourceSlotIndex = sourceSlotIndex;
        }
    }
}
