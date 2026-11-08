package com.example.flymestatusbarsizer.feature.ime;

import com.example.flymestatusbarsizer.MainActivity;
import com.example.flymestatusbarsizer.config.RemoteSettingsSync;
import com.example.flymestatusbarsizer.config.SettingsStore;

import android.content.ClipData;
import android.inputmethodservice.InputMethodService;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.TextUtils;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.CheckBox;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.libxposed.service.XposedService;

public final class ImeToolbarEditor {
    private static final String PHONE_PACKAGE = "com.android.phone";
    private final MainActivity activity;
    private final ArrayList<String> buttonSlots = new ArrayList<>();
    private final ArrayList<String> textSlots = new ArrayList<>();

    private LinearLayout buttonContainer;
    private LinearLayout buttonPoolContainer;
    private LinearLayout buttonSlotContainer;
    private LinearLayout textSlotContainer;
    private TextView applyButton;
    private TextView applyStateView;
    private String appliedSlotsSerialized = SettingsStore.DEFAULT_IME_CONTROL_BAR_BUTTON_SLOTS;
    private String appliedTextSlotsSerialized = SettingsStore.DEFAULT_IME_CONTROL_BAR_TEXT_SLOTS;

    public ImeToolbarEditor(MainActivity activity) {
        this.activity = activity;
    }

    public LinearLayout buildSettingsContent() {
        LinearLayout details = new LinearLayout(activity);
        details.setOrientation(LinearLayout.VERTICAL);

        activity.addActionButtonRow(details, "输入法作用域",
                "选择输入法及电话服务范围；验证码读取需要启用电话服务范围。新增范围需经 LSPosed 确认。",
                "管理", this::showImeScopes);
        activity.addDivider(details);
        activity.addSwitchRow(details, "显示扩展栏",
                "在控制栏上方增加一行文字内容，高度为下栏的一半。",
                SettingsStore.KEY_IME_CONTROL_BAR_EXPANSION_ENABLED,
                SettingsStore.DEFAULT_IME_CONTROL_BAR_EXPANSION_ENABLED);
        activity.addDivider(details);

        activity.addApplySliderRow(details, "输入法图标大小",
                "调节覆盖层里图标的大小。数值越大，图标越大。",
                SettingsStore.KEY_IME_CONTROL_BAR_ICON_SCALE_PERCENT,
                SettingsStore.DEFAULT_IME_CONTROL_BAR_ICON_SCALE_PERCENT,
                60, 180, "%");
        activity.addDivider(details);
        activity.addApplySliderRow(details, "输入法图标透明度",
                "调节覆盖层图标透明度。100% 完全不透明，数值越小越淡。",
                SettingsStore.KEY_IME_CONTROL_BAR_ICON_ALPHA_PERCENT,
                SettingsStore.DEFAULT_IME_CONTROL_BAR_ICON_ALPHA_PERCENT,
                10, 100, "%");
        activity.addDivider(details);
        activity.addApplyPositionOffsetSliderRow(details, "输入法控制栏上下位置",
                "正数向上、负数向下；滑块按 1dp 粗调，点右侧数值可输入 0.1dp。",
                SettingsStore.KEY_IME_CONTROL_BAR_Y_OFFSET_DP,
                SettingsStore.DEFAULT_IME_CONTROL_BAR_Y_OFFSET_DP * 10);
        activity.addDivider(details);
        activity.addProfileSectionHeader(details, "按钮位置与显隐",
                "长按按钮拖到下面固定槽位里就会显示；拖回按钮池就会隐藏。从左到右就是工具栏里的实际位置；拖拽后点击应用才会写入配置并刷新当前输入法界面。");

        buttonContainer = new LinearLayout(activity);
        buttonContainer.setOrientation(LinearLayout.VERTICAL);
        buttonContainer.setPadding(0, activity.dp(8), 0, 0);
        details.addView(buttonContainer, activity.matchWrap());
        loadConfig();
        renderEditor();
        return details;
    }

    private void showImeScopes() {
        activity.showToast("正在读取输入法作用域…");
        new Thread(() -> {
            Set<String> enabledScope = RemoteSettingsSync.readEnabledScope();
            InputMethodManager manager = (InputMethodManager)
                    activity.getSystemService(InputMethodService.INPUT_METHOD_SERVICE);
            List<InputMethodInfo> inputMethods = manager == null
                    ? new ArrayList<>() : manager.getEnabledInputMethodList();
            activity.runOnUiThread(() -> renderImeScopes(inputMethods, enabledScope));
        }, "ime-scope-reader").start();
    }

    private void renderImeScopes(List<InputMethodInfo> inputMethods, Set<String> enabledScope) {
        if (enabledScope == null) {
            activity.showToast("无法连接 LSPosed 服务，请确认模块已启用后重试");
            return;
        }
        if (inputMethods.isEmpty()) {
            activity.showToast("系统没有已启用的输入法");
            return;
        }
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        Set<String> displayedPackages = new HashSet<>();
        if (displayedPackages.add(PHONE_PACKAGE)) {
            addScopeCheckbox(list, "电话服务（短信验证码读取）", PHONE_PACKAGE,
                    enabledScope.contains(PHONE_PACKAGE));
        }
        for (InputMethodInfo inputMethod : inputMethods) {
            String packageName = inputMethod.getPackageName();
            if (!displayedPackages.add(packageName)) {
                continue;
            }
            addScopeCheckbox(list,
                    inputMethod.loadLabel(activity.getPackageManager()).toString(),
                    packageName, enabledScope.contains(packageName));
        }
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(list);
        new android.app.AlertDialog.Builder(activity)
                .setTitle("输入法作用域")
                .setView(scroll)
                .setPositiveButton("完成", null)
                .show();
    }

    private void addScopeCheckbox(LinearLayout list, String label, String packageName,
            boolean enabled) {
        CheckBox checkbox = new CheckBox(activity);
        checkbox.setText(label + "\n" + packageName);
        checkbox.setTextColor(activity.textColor());
        checkbox.setMinHeight(activity.dp(52));
        checkbox.setChecked(enabled);
        checkbox.setOnCheckedChangeListener((button, checked) -> {
            button.setEnabled(false);
            if (checked) {
                RemoteSettingsSync.requestScope(List.of(packageName),
                        new XposedService.OnScopeEventListener() {
                            @Override
                            public void onScopeRequestApproved(List<String> approved) {
                                activity.runOnUiThread(() -> {
                                    button.setEnabled(true);
                                    button.setChecked(true);
                                    activity.showToast("已添加作用域");
                                });
                            }

                            @Override
                            public void onScopeRequestFailed(String message) {
                                activity.runOnUiThread(() -> {
                                    button.setEnabled(true);
                                    button.setChecked(false);
                                    activity.showToast("添加失败：" + message);
                                });
                            }
                        });
            } else {
                RemoteSettingsSync.removeScope(List.of(packageName));
                button.setEnabled(true);
                activity.showToast("已移除作用域");
            }
        });
        list.addView(checkbox, activity.matchWrap());
    }

    private void loadConfig() {
        appliedSlotsSerialized = activity.readStringSetting(
                SettingsStore.KEY_IME_CONTROL_BAR_BUTTON_SLOTS,
                SettingsStore.DEFAULT_IME_CONTROL_BAR_BUTTON_SLOTS);
        appliedTextSlotsSerialized = activity.readStringSetting(
                SettingsStore.KEY_IME_CONTROL_BAR_TEXT_SLOTS,
                SettingsStore.DEFAULT_IME_CONTROL_BAR_TEXT_SLOTS);
        buttonSlots.clear();
        buttonSlots.addAll(ImeToolbarSpec.resolveButtonSlots(appliedSlotsSerialized));
        textSlots.clear();
        textSlots.addAll(ImeToolbarSpec.resolveTextSlots(appliedTextSlotsSerialized));
        appliedTextSlotsSerialized = ImeToolbarSpec.serializeTextSlots(textSlots);
        removeTextActionsFromButtonSlots();
    }

    private void renderEditor() {
        if (buttonContainer == null) {
            return;
        }
        buttonContainer.removeAllViews();
        activity.addProfileSectionHeader(buttonContainer, "按钮池",
                "拖到下栏可放置普通按钮；文字内容可拖到上栏。拖回按钮池即可隐藏。");

        buttonPoolContainer = new LinearLayout(activity);
        buttonPoolContainer.setOrientation(LinearLayout.VERTICAL);
        buttonPoolContainer.setPadding(activity.dp(12), activity.dp(8), activity.dp(12), activity.dp(8));
        buttonPoolContainer.setBackground(buildPoolBackground(false));
        buttonPoolContainer.setOnDragListener(this::handlePoolDrag);
        buttonContainer.addView(buttonPoolContainer, activity.matchWrapWithTop(8));

        activity.addProfileSectionHeader(buttonContainer, "下栏：7 个固定槽位",
                "从左到右对应工具栏的实际位置；空槽位会保留占位。");

        buttonSlotContainer = new LinearLayout(activity);
        buttonSlotContainer.setOrientation(LinearLayout.HORIZONTAL);
        buttonSlotContainer.setPadding(0, activity.dp(8), 0, 0);
        buttonContainer.addView(buttonSlotContainer, activity.matchWrap());

        activity.addProfileSectionHeader(buttonContainer, "上栏：文字内容",
                "验证码占两个槽位；上栏仅放文字内容。扩展栏关闭时，上栏设置仍会保留。");
        textSlotContainer = new LinearLayout(activity);
        textSlotContainer.setOrientation(LinearLayout.HORIZONTAL);
        textSlotContainer.setPadding(0, activity.dp(8), 0, 0);
        buttonContainer.addView(textSlotContainer, activity.matchWrap());

        LinearLayout actionRow = new LinearLayout(activity);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        actionRow.setPadding(0, activity.dp(8), 0, 0);

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
        renderTextSlots();
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
        for (String slotButton : textSlots) {
            if (!TextUtils.isEmpty(slotButton)) {
                poolButtons.remove(slotButton);
            }
        }
        if (poolButtons.isEmpty()) {
            TextView empty = new TextView(activity);
            empty.setText("所有按钮都已放入上下栏。");
            empty.setTextColor(activity.subtextColor());
            empty.setTextSize(13);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, activity.dp(8), 0, activity.dp(8));
            buttonPoolContainer.addView(empty, activity.matchWrap());
            return;
        }
        for (int i = 0; i < poolButtons.size(); i += MainActivity.IME_CONTROL_BAR_POOL_ROW_ITEM_COUNT) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            if (i > 0) {
                row.setPadding(0, activity.dp(6), 0, 0);
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
        activity.addSearchItem(item, ImeToolbarSpec.getButtonLabel(button), "输入法控制栏按钮");
        item.setTextColor(activity.textColor());
        item.setTextSize(14);
        item.setGravity(Gravity.CENTER);
        item.setMinHeight(activity.dp(40));
        item.setPadding(activity.dp(8), activity.dp(8), activity.dp(8), activity.dp(8));
        item.setBackground(activity.outlinedRect(activity.surfaceColor(), activity.strokeColor(), 1, 18));
        item.setOnLongClickListener(v -> startDrag(v, button, -1, false));
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
            View slotView = buildSlotView(i, button, false);
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

    private void renderTextSlots() {
        if (textSlotContainer == null) {
            return;
        }
        textSlotContainer.removeAllViews();
        for (int i = 0; i < ImeToolbarSpec.getButtonSlotCount(); i++) {
            String button = i < textSlots.size() ? textSlots.get(i) : null;
            int span = ImeToolbarSpec.getButtonSpan(button);
            View slotView = buildSlotView(i, button, true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, span);
            if (i > 0) {
                lp.leftMargin = activity.dp(6);
            }
            textSlotContainer.addView(slotView, lp);
            if (span > 1) {
                i += span - 1;
            }
        }
    }

    private View buildSlotView(int slotIndex, String button, boolean textRow) {
        ArrayList<String> slots = textRow ? textSlots : buttonSlots;
        boolean covered = isCoveredByWideButton(slots, slotIndex);
        LinearLayout slot = new LinearLayout(activity);
        slot.setOrientation(LinearLayout.VERTICAL);
        slot.setGravity(Gravity.CENTER);
        slot.setPadding(activity.dp(4), activity.dp(8), activity.dp(4), activity.dp(8));
        slot.setMinimumHeight(activity.dp(60));
        slot.setTag(Integer.valueOf(slotIndex));
        slot.setBackground(buildSlotBackground(!TextUtils.isEmpty(button) || covered, false));
        slot.setOnDragListener((target, event) -> handleSlotDrag(target, event, textRow));
        if (!TextUtils.isEmpty(button) && !covered) {
            activity.addSearchItem(slot, ImeToolbarSpec.getButtonLabel(button),
                    textRow ? "输入法控制栏文字内容" : "输入法控制栏按钮");
            slot.setOnLongClickListener(v -> startDrag(v, button, slotIndex, textRow));
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

    private boolean startDrag(View view, String button, int sourceSlotIndex,
            boolean sourceTextRow) {
        if (view == null || TextUtils.isEmpty(button)) {
            return false;
        }
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        ClipData data = ClipData.newPlainText(MainActivity.IME_CONTROL_BAR_DRAG_LABEL, button);
        DragState state = new DragState(view, button, sourceSlotIndex, sourceTextRow);
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

    private boolean handleSlotDrag(View target, DragEvent event, boolean textRow) {
        if (!(target.getTag() instanceof Integer)) {
            return false;
        }
        int slotIndex = (Integer) target.getTag();
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                DragState dragState = event.getLocalState() instanceof DragState
                        ? (DragState) event.getLocalState() : null;
                return dragState != null
                        && (!textRow || ImeToolbarSpec.isTextContentAction(dragState.button));
            case DragEvent.ACTION_DRAG_ENTERED:
                target.setBackground(buildSlotBackground(isOccupied(textRow ? textSlots : buttonSlots,
                        slotIndex), true));
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
                target.setBackground(buildSlotBackground(isOccupied(textRow ? textSlots : buttonSlots,
                        slotIndex), false));
                return true;
            case DragEvent.ACTION_DROP:
                target.setBackground(buildSlotBackground(isOccupied(textRow ? textSlots : buttonSlots,
                        slotIndex), false));
                DragState state = event.getLocalState() instanceof DragState
                        ? (DragState) event.getLocalState() : null;
                if (state == null || (textRow
                        && !ImeToolbarSpec.isTextContentAction(state.button))) {
                    return false;
                }
                moveButtonToSlot(state, slotIndex, textRow);
                syncEditor();
                return true;
            case DragEvent.ACTION_DRAG_ENDED:
                target.setBackground(buildSlotBackground(isOccupied(textRow ? textSlots : buttonSlots,
                        slotIndex), false));
                restoreDraggedView(event.getLocalState());
                return true;
            default:
                return true;
        }
    }

    private void moveButtonToSlot(DragState state, int targetSlotIndex, boolean targetTextRow) {
        String button = state.button;
        ArrayList<String> targetSlots = targetTextRow ? textSlots : buttonSlots;
        ArrayList<String> sourceSlots = state.sourceTextRow ? textSlots : buttonSlots;
        if (TextUtils.isEmpty(button) || (targetTextRow
                && !ImeToolbarSpec.isTextContentAction(button))
                || targetSlotIndex < 0 || targetSlotIndex >= targetSlots.size()) {
            return;
        }
        int span = ImeToolbarSpec.getButtonSpan(button);
        if (targetSlotIndex + span > targetSlots.size()) {
            targetSlotIndex = targetSlots.size() - span;
        }
        int sourceSlotIndex = state.sourceSlotIndex < 0
                ? -1 : findButtonSlotIndex(sourceSlots, button);
        if (sourceSlots == targetSlots && sourceSlotIndex == targetSlotIndex) {
            return;
        }
        if (sourceSlotIndex >= 0) {
            clearButtonSpan(sourceSlots, sourceSlotIndex);
        }
        for (int i = 0; i < span; i++) {
            clearSlotOrOwner(targetSlots, targetSlotIndex + i);
        }
        targetSlots.set(targetSlotIndex, button);
        if (targetTextRow) {
            normalizeTextSlots();
        } else {
            normalizeButtonSlots();
        }
    }

    private void removeButtonFromSlots(String button) {
        int slotIndex = findButtonSlotIndex(buttonSlots, button);
        if (slotIndex >= 0) {
            clearButtonSpan(buttonSlots, slotIndex);
            normalizeButtonSlots();
        }
        slotIndex = findButtonSlotIndex(textSlots, button);
        if (slotIndex >= 0) {
            clearButtonSpan(textSlots, slotIndex);
            normalizeTextSlots();
        }
    }

    private void removeTextActionsFromButtonSlots() {
        for (int i = 0; i < textSlots.size(); i++) {
            String button = textSlots.get(i);
            if (!TextUtils.isEmpty(button)) {
                int lowerIndex = findButtonSlotIndex(buttonSlots, button);
                if (lowerIndex >= 0) {
                    clearButtonSpan(buttonSlots, lowerIndex);
                }
            }
        }
        normalizeButtonSlots();
    }

    private int findButtonSlotIndex(ArrayList<String> slots, String button) {
        if (TextUtils.isEmpty(button)) {
            return -1;
        }
        for (int i = 0; i < slots.size(); i++) {
            if (button.equals(slots.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private boolean isOccupied(ArrayList<String> slots, int slotIndex) {
        return slotIndex < slots.size()
                && (!TextUtils.isEmpty(slots.get(slotIndex))
                        || isCoveredByWideButton(slots, slotIndex));
    }

    private boolean isCoveredByWideButton(ArrayList<String> slots, int slotIndex) {
        int owner = findWideButtonOwner(slots, slotIndex);
        return owner >= 0 && owner != slotIndex;
    }

    private int findWideButtonOwner(ArrayList<String> slots, int slotIndex) {
        for (int i = 0; i < slots.size(); i++) {
            String button = slots.get(i);
            int span = ImeToolbarSpec.getButtonSpan(button);
            if (!TextUtils.isEmpty(button) && span > 1 && slotIndex >= i && slotIndex < i + span) {
                return i;
            }
        }
        return -1;
    }

    private void clearSlotOrOwner(ArrayList<String> slots, int slotIndex) {
        int owner = findWideButtonOwner(slots, slotIndex);
        if (owner >= 0) {
            clearButtonSpan(slots, owner);
        } else if (slotIndex >= 0 && slotIndex < slots.size()) {
            slots.set(slotIndex, null);
        }
    }

    private void clearButtonSpan(ArrayList<String> slots, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= slots.size()) {
            return;
        }
        String button = slots.get(slotIndex);
        int span = ImeToolbarSpec.getButtonSpan(button);
        for (int i = 0; i < span && slotIndex + i < slots.size(); i++) {
            slots.set(slotIndex + i, null);
        }
    }

    private void normalizeButtonSlots() {
        ArrayList<String> normalized =
                ImeToolbarSpec.resolveButtonSlots(ImeToolbarSpec.serializeButtonSlots(buttonSlots));
        buttonSlots.clear();
        buttonSlots.addAll(normalized);
    }

    private void normalizeTextSlots() {
        ArrayList<String> normalized =
                ImeToolbarSpec.resolveTextSlots(ImeToolbarSpec.serializeTextSlots(textSlots));
        textSlots.clear();
        textSlots.addAll(normalized);
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
        return !TextUtils.equals(ImeToolbarSpec.serializeButtonSlots(buttonSlots), appliedSlotsSerialized)
                || !TextUtils.equals(ImeToolbarSpec.serializeTextSlots(textSlots),
                        appliedTextSlotsSerialized);
    }

    private void syncApplyState() {
        boolean changed = hasPendingChanges();
        if (applyStateView != null) {
        applyStateView.setText(changed ? "有未应用的修改" : "当前修改已应用");
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
        appliedTextSlotsSerialized = ImeToolbarSpec.serializeTextSlots(textSlots);
        SharedPreferences.Editor editor = activity.prefs().edit();
        editor.putString(SettingsStore.KEY_IME_CONTROL_BAR_BUTTON_SLOTS, appliedSlotsSerialized);
        editor.putString(SettingsStore.KEY_IME_CONTROL_BAR_TEXT_SLOTS, appliedTextSlotsSerialized);
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
        private final boolean sourceTextRow;

        private DragState(View sourceView, String button, int sourceSlotIndex,
                boolean sourceTextRow) {
            this.sourceView = sourceView;
            this.button = button;
            this.sourceSlotIndex = sourceSlotIndex;
            this.sourceTextRow = sourceTextRow;
        }
    }
}
