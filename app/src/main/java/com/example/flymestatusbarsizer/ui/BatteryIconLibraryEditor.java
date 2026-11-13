package com.example.flymestatusbarsizer.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import com.example.flymestatusbarsizer.MainActivity;
import com.example.flymestatusbarsizer.R;
import com.example.flymestatusbarsizer.config.ModuleConfig;
import com.example.flymestatusbarsizer.config.SettingsStore;
import com.example.flymestatusbarsizer.feature.battery.BatteryIconLibrary;
import com.example.flymestatusbarsizer.feature.battery.BatteryIconLibrary.Entry;
import com.example.flymestatusbarsizer.feature.battery.BatteryIconPack;
import com.example.flymestatusbarsizer.feature.battery.BatteryIconPainter;
import com.example.flymestatusbarsizer.feature.statusbar.RingerVectorXml;

import org.json.JSONException;
import org.json.JSONObject;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class BatteryIconLibraryEditor {
    private static final String[] XML_KEYS = {"shellXml", "fillXml", "backgroundXml", "chargingXml", "quickChargingXml"};
    private static final String[] DIRECTIONS = {"left_to_right", "right_to_left", "bottom_to_top", "top_to_bottom"};
    private final MainActivity activity;
    private final Runnable onChanged;
    private BatteryIconLibrary library;
    private LinearLayout list;

    private BatteryIconLibraryEditor(MainActivity activity, Runnable onChanged) {
        this.activity = activity;
        this.onChanged = onChanged;
        library = BatteryIconLibrary.load(activity.prefs());
    }

    public static void addSettingsRow(MainActivity activity, LinearLayout parent) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(activity.dp(64));
        TextView title = new TextView(activity);
        title.setText("电池图标库");
        title.setTextSize(15);
        title.setTextColor(activity.textColor());
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        PreviewView preview = new PreviewView(activity, false, false);
        row.addView(preview, new LinearLayout.LayoutParams(activity.dp(90), activity.dp(48)));
        TextView name = new TextView(activity);
        name.setTextSize(13);
        name.setTextColor(activity.subtextColor());
        name.setMaxWidth(activity.dp(110));
        row.addView(name);
        Runnable refresh = () -> {
            Entry entry = BatteryIconLibrary.load(activity.prefs()).selectedEntry();
            name.setText(entry.pack().name + "  ›");
            preview.pack = entry.pack();
            preview.invalidate();
        };
        refresh.run();
        activity.setTapClickListener(row, v -> new BatteryIconLibraryEditor(activity, refresh).show());
        parent.addView(row);
    }

    private void show() {
        Dialog dialog = new Dialog(activity);
        LinearLayout root = page(dialog, "电池图标库");
        LinearLayout actions = column();
        actions.setPadding(activity.dp(16), 0, activity.dp(16), activity.dp(8));
        LinearLayout buttons = new LinearLayout(activity);
        addButton(buttons, "新增", () -> edit(null, true));
        addButton(buttons, "粘贴图标包", this::pastePack);
        actions.addView(buttons);
        root.addView(actions);
        ScrollView scroll = new ScrollView(activity);
        list = column();
        list.setPadding(activity.dp(16), activity.dp(8), activity.dp(16), activity.dp(24));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        renderList();
        showPage(dialog, root);
        if (library.error != null) activity.showToast(library.error);
    }

    private void renderList() {
        list.removeAllViews();
        for (Entry entry : library.entries) {
            boolean selected = library.selected.equals(entry.id());
            LinearLayout row = new LinearLayout(activity);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(activity.dp(8), activity.dp(6), 0, activity.dp(6));
            row.setBackground(activity.roundRect(selected ? activity.primaryContainerColor() : activity.surfaceSoftColor(), 8));
            PreviewView icon = new PreviewView(activity, false, false);
            icon.pack = entry.pack();
            row.addView(icon, new LinearLayout.LayoutParams(activity.dp(92), activity.dp(56)));
            TextView name = text(entry.pack().name + (selected ? "  ✓" : ""), 15);
            row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            activity.setTapClickListener(row, v -> persist(new BatteryIconLibrary(library.entries, entry.id())));
            TextView more = text("⋮", 24);
            more.setGravity(Gravity.CENTER);
            more.setContentDescription(entry.pack().name + "，更多操作");
            activity.setTapClickListener(more, v -> menu(entry));
            row.addView(more, new LinearLayout.LayoutParams(activity.dp(48), activity.dp(48)));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.bottomMargin = activity.dp(8);
            list.addView(row, params);
        }
    }

    private void menu(Entry entry) {
        String[] items = entry.builtIn() ? new String[]{"复制并编辑"}
                : new String[]{"修改", "复制并编辑", "重命名", "删除"};
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(entry.pack().name)
                .setItems(items, (d, index) -> {
                    switch (items[index]) {
                        case "修改" -> edit(entry, false);
                        case "复制并编辑" -> edit(entry, true);
                        case "重命名" -> rename(entry);
                        case "删除" -> {
                            Runnable remove = () -> {
                                ArrayList<Entry> entries = new ArrayList<>(library.entries);
                                entries.remove(entry);
                                persist(new BatteryIconLibrary(entries, library.selected.equals(entry.id())
                                        ? BatteryIconLibrary.IOS : library.selected));
                            };
                            if (library.selected.equals(entry.id())) {
                                showAlert(new AlertDialog.Builder(activity).setTitle("删除图标包？")
                                        .setMessage("删除后使用内置类 iOS 图标包。")
                                        .setNegativeButton("取消", null).setPositiveButton("删除", (a, b) -> remove.run()).create());
                            } else remove.run();
                        }
                    }
                }).create();
        showAlert(dialog);
    }

    private String uniqueName(String base) {
        String name = base;
        for (int suffix = 2; nameExists(name, ""); suffix++) name = base + " " + suffix;
        return name;
    }

    private boolean nameExists(String name, String id) {
        return library.entries.stream().anyMatch(entry -> !entry.id().equals(id) && entry.pack().name.equals(name));
    }

    private void rename(Entry entry) {
        EditText name = input("名称", false);
        name.setText(entry.pack().name);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("重命名").setView(name)
                .setNegativeButton("取消", null).setPositiveButton("保存", null).create();
        showAlert(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = name.getText().toString().trim();
            if (title.isEmpty() || nameExists(title, entry.id())) { name.setError("名称为空或已存在"); return; }
            try {
                BatteryIconPack pack = BatteryIconPack.parse(new JSONObject(entry.pack().json).put("name", title).toString());
                ArrayList<Entry> entries = new ArrayList<>(library.entries);
                entries.set(entries.indexOf(entry), new Entry(entry.id(), pack));
                persist(new BatteryIconLibrary(entries, library.selected));
                dialog.dismiss();
            } catch (JSONException invalid) { name.setError(invalid.getMessage()); }
        });
    }

    private void pastePack() {
        EditText json = input("粘贴完整图标包 JSON", true);
        json.setMinLines(6);
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard.hasPrimaryClip()) json.setText(clipboard.getPrimaryClip().getItemAt(0).coerceToText(activity));
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("粘贴图标包").setView(json)
                .setNegativeButton("取消", null).setPositiveButton("导入并编辑", null).create();
        showAlert(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                BatteryIconPack pack = BatteryIconPack.parse(json.getText().toString());
                dialog.dismiss();
                edit(new Entry(UUID.randomUUID().toString(), pack), true);
            } catch (IllegalArgumentException invalid) { json.setError(invalid.getMessage()); }
        });
    }

    private void edit(Entry original, boolean duplicate) {
        BatteryIconPack template = original == null ? library.selectedEntry().pack() : original.pack();
        JSONObject source;
        try {
            source = new JSONObject(template.json);
            if (original == null) {
                source.put("textOffsetX", 0).put("textOffsetY", 0);
                template = BatteryIconPack.parse(source.toString());
            }
        }
        catch (JSONException impossible) { throw new IllegalStateException(impossible); }
        String id = original == null || duplicate ? UUID.randomUUID().toString() : original.id();
        Dialog dialog = new Dialog(activity);
        LinearLayout root = page(dialog, original == null || duplicate ? "新增电池图标包" : "修改电池图标包");
        LinearLayout header = (LinearLayout) root.getChildAt(0);
        TextView heading = (TextView) header.getChildAt(1);
        heading.setSingleLine(true);
        heading.setEllipsize(TextUtils.TruncateAt.END);
        heading.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        ScrollView scroll = new ScrollView(activity);
        LinearLayout content = column();
        content.setPadding(activity.dp(16), activity.dp(16), activity.dp(16), activity.dp(12));
        LinearLayout previewPanel = column();
        previewPanel.setPadding(activity.dp(16), 0, activity.dp(16), 0);
        LinearLayout previewTools = new LinearLayout(activity);
        previewTools.setOrientation(LinearLayout.VERTICAL);
        previewTools.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout previewRow = new LinearLayout(activity);
        previewRow.setGravity(Gravity.CENTER_VERTICAL);
        previewPanel.addView(previewRow);
        LinearLayout previews = new LinearLayout(activity);
        PreviewView light = new PreviewView(activity, false, true);
        PreviewView dark = new PreviewView(activity, true, true);
        light.pack = dark.pack = template;
        previews.addView(light, new LinearLayout.LayoutParams(0, activity.dp(60), 1));
        previews.addView(dark, new LinearLayout.LayoutParams(0, activity.dp(60), 1));
        previewRow.addView(previews, new LinearLayout.LayoutParams(0, activity.dp(60), 1));
        previewRow.addView(previewTools, new LinearLayout.LayoutParams(activity.dp(104), activity.dp(60)));
        LinearLayout levelRow = new LinearLayout(activity);
        levelRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView levelLabel = text("82%", 12);
        levelRow.addView(levelLabel, new LinearLayout.LayoutParams(activity.dp(48), -2));
        SeekBar level = new SeekBar(activity);
        level.setContentDescription("预览电量");
        activity.styleSeekBar(level);
        level.setMax(100);
        level.setProgress(82);
        level.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int value, boolean user) {
                light.level = dark.level = value;
                light.invalidate(); dark.invalidate(); levelLabel.setText(value + "%");
            }
            public void onStartTrackingTouch(SeekBar bar) { }
            public void onStopTrackingTouch(SeekBar bar) { }
        });
        levelRow.addView(level, new LinearLayout.LayoutParams(0, activity.dp(36), 1));
        Switch numbers = new Switch(activity);
        activity.styleSwitch(numbers);
        numbers.setMinHeight(activity.dp(36));
        numbers.setText("数字");
        numbers.setTextSize(12);
        numbers.setTextColor(activity.textColor());
        numbers.setSwitchPadding(activity.dp(4));
        numbers.setContentDescription("预览电量数字");
        numbers.setChecked(true);
        numbers.setOnCheckedChangeListener((button, checked) -> {
            activity.performTapHaptic(button);
            light.showNumbers = dark.showNumbers = checked;
            light.invalidate();
            dark.invalidate();
        });
        levelRow.addView(numbers, new LinearLayout.LayoutParams(-2, activity.dp(36)));
        previewPanel.addView(levelRow);
        Spinner state = spinner(new String[]{"未充电", "已插电", "普通充电", "快充"});
        state.setPadding(activity.dp(8), 0, 0, 0);
        state.setContentDescription("预览充电状态");
        state.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long itemId) {
                TextView label = (TextView) view;
                label.setTextSize(12);
                label.setSingleLine(true);
                label.setPadding(0, 0, 0, 0);
                light.state = dark.state = position; light.invalidate(); dark.invalidate();
            }
            public void onNothingSelected(AdapterView<?> parent) { }
        });
        previewTools.addView(state, new LinearLayout.LayoutParams(-1, activity.dp(28)));
        root.addView(previewPanel);
        ImageView handle = new ImageView(activity);
        handle.setImageResource(R.drawable.ic_section_expand);
        handle.setImageTintList(ColorStateList.valueOf(activity.subtextColor()));
        handle.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        handle.setPadding(0, activity.dp(2), 0, activity.dp(2));
        handle.setRotation(180);
        handle.setContentDescription("收起预览");
        activity.setTapClickListener(handle, v -> {
            boolean expanded = previewPanel.getVisibility() == View.VISIBLE;
            previewPanel.setVisibility(expanded ? View.GONE : View.VISIBLE);
            handle.setRotation(expanded ? 0 : 180);
            handle.setContentDescription(expanded ? "展开预览" : "收起预览");
        });
        root.addView(handle, new LinearLayout.LayoutParams(-1, activity.dp(16)));
        LinkedHashMap<String, EditText> fields = new LinkedHashMap<>();
        LinkedHashMap<String, SeekBar> styleSliders = new LinkedHashMap<>();
        LinearLayout basic = editorSection(content, "基本信息");
        LinearLayout typography = editorSection(content, "数字样式");
        LinearLayout offsetRow = new LinearLayout(activity);
        LinearLayout styleRow = new LinearLayout(activity);
        LinearLayout alphaRow = new LinearLayout(activity);
        typography.addView(offsetRow, new LinearLayout.LayoutParams(-1, -2));
        typography.addView(styleRow, new LinearLayout.LayoutParams(-1, -2));
        typography.addView(alphaRow, new LinearLayout.LayoutParams(-1, -2));
        String[][] labels = {{"name", "名称", "名称"}, {"textOffsetX", "水平偏移", "数字水平偏移"},
                {"textOffsetY", "垂直偏移", "数字垂直偏移"}, {"textSize", "基础字号", "数字基础字号"},
                {"textOutline", "描边宽度", "数字描边宽度"}, {"textAlpha", "透明度 0–1", "数字透明度 0–1"}};
        for (int i = 0; i < labels.length; i++) {
            String[] label = labels[i];
            LinearLayout fieldRow = column();
            fieldRow.setPadding(0, activity.dp(8), 0, 0);
            TextView fieldLabel = text(label[1], i == 0 ? 13 : 11);
            if (i != 0) {
                fieldLabel.setTextColor(activity.subtextColor());
                fieldLabel.setTypeface(null, Typeface.BOLD);
            }
            fieldRow.addView(fieldLabel);
            EditText field = input(label[1], false);
            field.setContentDescription(label[2]);
            field.setMinHeight(activity.dp(48));
            field.setText(source.optString(label[0], label[0].equals("textAlpha") ? "1" : "0"));
            if (!label[0].equals("name")) {
                field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                        | InputType.TYPE_NUMBER_FLAG_SIGNED);
                field.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
                field.setSelectAllOnFocus(true);
            }
            if (label[0].equals("name") && (original == null || duplicate)) {
                field.setText(uniqueName(original == null ? "自定义电池"
                        : library.entries.contains(original) ? template.name + " 副本" : template.name));
            }
            fields.put(label[0], field);
            if (i == 0) {
                fieldRow.addView(field, new LinearLayout.LayoutParams(-1, -2));
                basic.addView(fieldRow, new LinearLayout.LayoutParams(-1, -2));
            } else {
                SeekBar slider = new SeekBar(activity);
                activity.styleSeekBar(slider);
                slider.setContentDescription(label[2]);
                float factor = i == 5 ? 100f : 10f;
                float extent = i == 1 ? template.width : template.height;
                float maximum = switch (i) {
                    case 1, 2 -> extent / 2f;
                    case 3 -> extent * 2f;
                    case 4 -> extent / 5f;
                    default -> 1f;
                };
                int minimum = i <= 2 ? -Math.round(maximum * factor) : i == 3 ? 1 : 0;
                int current = Math.round((float) source.optDouble(label[0], 0) * factor);
                slider.setMin(Math.min(minimum, current));
                slider.setMax(Math.max(Math.round(maximum * factor), current));
                slider.setProgress(current);
                slider.setKeyProgressIncrement(1);
                styleSliders.put(label[0], slider);
                fieldRow.addView(new SliderValueView(activity, slider, field, factor),
                        new LinearLayout.LayoutParams(-1, activity.dp(88)));
                LinearLayout row = i <= 2 ? offsetRow : i <= 4 ? styleRow : alphaRow;
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
                if (i == 1 || i == 3) params.rightMargin = activity.dp(12);
                row.addView(fieldRow, params);
            }
        }
        TextView directionLabel = text("填充方向", 13);
        directionLabel.setPadding(0, activity.dp(8), 0, 0);
        basic.addView(directionLabel);
        Spinner direction = spinner(new String[]{"从左到右", "从右到左", "从下到上", "从上到下"});
        direction.setContentDescription("填充方向");
        for (int i = 0; i < DIRECTIONS.length; i++) if (DIRECTIONS[i].equals(source.optString("fillDirection"))) direction.setSelection(i);
        basic.addView(direction, new LinearLayout.LayoutParams(-1, activity.dp(48)));
        LinearLayout xmlSection = editorSection(content, "XML 图层");
        LinearLayout layers = new LinearLayout(activity);
        LinearLayout.LayoutParams layerParams = new LinearLayout.LayoutParams(-1, -2);
        layerParams.setMargins(0, activity.dp(8), 0, activity.dp(8));
        xmlSection.addView(layers, layerParams);
        String[] layerNames = {"外壳", "电芯", "空电量", "充电", "快充"};
        ArrayList<EditText> xmlFields = new ArrayList<>();
        for (int index = 0; index < XML_KEYS.length; index++) {
            String key = XML_KEYS[index];
            int position = index;
            LinearLayout card = column();
            card.setPadding(activity.dp(2), activity.dp(2), activity.dp(2), activity.dp(4));
            card.setSelected(index == 0);
            card.setBackground(activity.roundRect(index == 0
                    ? activity.primaryContainerColor() : activity.surfaceColor(), 8));
            card.setContentDescription(layerNames[index] + " XML" + (index >= 2 ? "，可选" : ""));
            LayerPreviewView preview = new LayerPreviewView(activity);
            card.addView(preview, new LinearLayout.LayoutParams(-1, activity.dp(72)));
            TextView label = text(layerNames[index], 11);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setAutoSizeTextTypeUniformWithConfiguration(9, 11, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
            label.setTextColor(index == 0 ? activity.primaryColor() : activity.textColor());
            card.addView(label, new LinearLayout.LayoutParams(-1, activity.dp(24)));
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(0, -2, 1);
            cardParams.setMargins(activity.dp(2), 0, activity.dp(2), 0);
            layers.addView(card, cardParams);
            EditText xml = input("XML", true);
            xml.setContentDescription(card.getContentDescription());
            xml.setTypeface(Typeface.MONOSPACE);
            xml.setTextSize(12);
            xml.setMinLines(6);
            xml.setGravity(Gravity.TOP | Gravity.START);
            xml.setPadding(activity.dp(8), activity.dp(8), activity.dp(8), activity.dp(8));
            xml.setBackground(activity.roundRect(activity.surfaceColor(), 8));
            xml.setText(source.optString(key, ""));
            xml.setVisibility(View.GONE);
            fields.put(key, xml);
            xmlFields.add(xml);
            xmlSection.addView(xml, new LinearLayout.LayoutParams(-1, -2));
            activity.setTapClickListener(card, v -> {
                for (int i = 0; i < xmlFields.size(); i++) {
                    boolean selected = i == position;
                    xmlFields.get(i).setVisibility(selected ? View.VISIBLE : View.GONE);
                    View item = layers.getChildAt(i);
                    item.setSelected(selected);
                    item.setBackground(activity.roundRect(selected
                            ? activity.primaryContainerColor() : activity.surfaceColor(), 8));
                    ((TextView) ((LinearLayout) item).getChildAt(1)).setTextColor(selected
                            ? activity.primaryColor() : activity.textColor());
                }
            });
            Runnable refreshLayer = () -> {
                String value = xml.getText().toString().trim();
                preview.vector = null;
                preview.message = null;
                xml.setError(null);
                if (value.isEmpty()) {
                    preview.message = position >= 2 ? "未设置" : "未填写";
                    if (position < 2) xml.setError("请填写" + layerNames[position] + " XML");
                } else {
                    try {
                        preview.vector = RingerVectorXml.parse(value);
                    } catch (IllegalArgumentException invalid) {
                        preview.message = "XML 有误";
                        xml.setError(invalid.getMessage());
                    }
                }
                preview.invalidate();
            };
            xml.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                public void onTextChanged(CharSequence s, int start, int before, int count) { }
                public void afterTextChanged(Editable value) {
                    xml.removeCallbacks(refreshLayer);
                    xml.postDelayed(refreshLayer, 300);
                }
            });
            refreshLayer.run();
        }
        xmlFields.get(0).setVisibility(View.VISIBLE);
        TextView error = text("", 13);
        error.setTextColor(0xFFD32F2F);
        error.setVisibility(View.GONE);
        content.addView(error);
        Consumer<Boolean> refreshPreview = showErrors -> {
            BatteryIconPack pack = readForm(fields, direction, id, error, showErrors);
            if (pack == null) return;
            for (Map.Entry<String, SeekBar> control : styleSliders.entrySet()) {
                float value = switch (control.getKey()) {
                    case "textOffsetX" -> pack.textOffsetX;
                    case "textOffsetY" -> pack.textOffsetY;
                    case "textSize" -> pack.textSize;
                    case "textOutline" -> pack.textOutline;
                    default -> pack.textAlpha;
                };
                int progress = Math.round(value * (control.getKey().equals("textAlpha") ? 100f : 10f));
                SeekBar slider = control.getValue();
                slider.setMin(Math.min(slider.getMin(), progress));
                slider.setMax(Math.max(slider.getMax(), progress));
                slider.setProgress(progress);
                ((SliderValueView) slider.getParent()).positionValue();
            }
            light.pack = dark.pack = pack;
            light.invalidate();
            dark.invalidate();
        };
        for (Map.Entry<String, SeekBar> control : styleSliders.entrySet()) {
            EditText field = fields.get(control.getKey());
            float factor = control.getKey().equals("textAlpha") ? 100f : 10f;
            control.getValue().setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar bar, int progress, boolean user) {
                    ((SliderValueView) bar.getParent()).positionValue();
                    if (!user) return;
                    bar.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    field.setText(BigDecimal.valueOf(progress)
                            .divide(BigDecimal.valueOf((int) factor)).stripTrailingZeros().toPlainString());
                    field.setSelection(field.length());
                }
                public void onStartTrackingTouch(SeekBar bar) {
                    ((SliderValueView) bar.getParent()).requestFocus();
                    InputMethodManager keyboard = activity.getSystemService(InputMethodManager.class);
                    keyboard.hideSoftInputFromWindow(field.getWindowToken(), 0);
                }
                public void onStopTrackingTouch(SeekBar bar) { }
            });
            field.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                public void onTextChanged(CharSequence s, int start, int before, int count) { }
                public void afterTextChanged(Editable value) { refreshPreview.accept(false); }
            });
        }
        addButton(previewTools, "更新预览", () -> refreshPreview.accept(true));
        TextView updatePreview = (TextView) previewTools.getChildAt(1);
        updatePreview.setMinHeight(0);
        updatePreview.setTextSize(12);
        updatePreview.setSingleLine(true);
        updatePreview.setPadding(0, 0, 0, 0);
        updatePreview.setLayoutParams(new LinearLayout.LayoutParams(-1, activity.dp(28)));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView save = text("保存", 14);
        save.setGravity(Gravity.CENTER);
        save.setTextColor(activity.primaryColor());
        activity.setTapClickListener(save, v -> {
            BatteryIconPack pack = readForm(fields, direction, id, error, true);
            if (pack == null) return;
            ArrayList<Entry> entries = new ArrayList<>(library.entries);
            entries.removeIf(entry -> entry.id().equals(id));
            entries.add(new Entry(id, pack));
            persist(new BatteryIconLibrary(entries, library.selected));
            dialog.dismiss();
            activity.showToast("已保存");
        });
        header.addView(save, new LinearLayout.LayoutParams(activity.dp(64), activity.dp(48)));
        showPage(dialog, root);
    }

    private LinearLayout editorSection(LinearLayout parent, String title) {
        LinearLayout section = column();
        section.setPadding(activity.dp(12), activity.dp(12), activity.dp(12), activity.dp(12));
        section.setBackground(activity.roundRect(activity.surfaceSoftColor(), 12));
        TextView heading = text(title, 14);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setTextColor(activity.primaryColor());
        section.addView(heading);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = activity.dp(16);
        parent.addView(section, params);
        return section;
    }

    private BatteryIconPack readForm(Map<String, EditText> fields, Spinner direction, String id, TextView error,
            boolean showErrors) {
        try {
            JSONObject object = new JSONObject().put("version", 2).put("fillDirection", DIRECTIONS[direction.getSelectedItemPosition()]);
            for (Map.Entry<String, EditText> field : fields.entrySet()) {
                String key = field.getKey(), value = field.getValue().getText().toString().trim();
                if (key.equals("name") || key.endsWith("Xml")) object.put(key, value);
                else object.put(key, Double.parseDouble(value));
            }
            BatteryIconPack pack = BatteryIconPack.parse(object.toString());
            if (nameExists(pack.name, id)) throw new IllegalArgumentException("同名图标包已存在，请修改名称");
            error.setVisibility(View.GONE);
            return pack;
        } catch (JSONException | IllegalArgumentException invalid) {
            if (showErrors) {
                error.setText(invalid.getMessage());
                error.setVisibility(View.VISIBLE);
                error.post(() -> error.requestRectangleOnScreen(new Rect(0, 0, error.getWidth(), error.getHeight()), false));
            }
            return null;
        }
    }

    private void persist(BatteryIconLibrary updated) {
        library = updated;
        activity.prefs().edit().putString(SettingsStore.KEY_BATTERY_ICON_LIBRARY, library.encode()).apply();
        SettingsStore.notifyChanged(activity);
        renderList();
        onChanged.run();
    }

    private Spinner spinner(String[] values) {
        Spinner spinner = new Spinner(activity);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        return spinner;
    }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(activity);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private TextView text(String value, int size) {
        TextView view = new TextView(activity);
        view.setText(value); view.setTextSize(size); view.setTextColor(activity.textColor());
        return view;
    }

    private void addButton(LinearLayout row, String title, Runnable action) {
        TextView button = text(title, 14);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(activity.dp(48));
        button.setBackground(activity.roundRect(activity.primaryContainerColor(), 8));
        button.setTextColor(activity.primaryColor());
        activity.setTapClickListener(button, v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.setMargins(activity.dp(4), activity.dp(4), activity.dp(4), activity.dp(4));
        row.addView(button, params);
    }

    private EditText input(String hint, boolean multiline) {
        EditText view = new EditText(activity);
        view.setHint(hint); view.setTextColor(activity.textColor()); view.setHintTextColor(activity.subtextColor());
        view.setSingleLine(!multiline);
        view.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | (multiline ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        return view;
    }

    private LinearLayout page(Dialog dialog, String title) {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = column();
        root.setFitsSystemWindows(true);
        root.setBackgroundColor(activity.surfaceColor());
        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView back = new ImageView(activity);
        back.setImageResource(R.drawable.ic_settings_back);
        back.setImageTintList(ColorStateList.valueOf(activity.textColor()));
        back.setScaleType(ImageView.ScaleType.CENTER);
        back.setContentDescription("返回");
        activity.setTapClickListener(back, v -> dialog.dismiss());
        header.addView(back, new LinearLayout.LayoutParams(activity.dp(48), activity.dp(56)));
        TextView heading = text(title, 20);
        heading.setTypeface(null, Typeface.BOLD);
        header.addView(heading);
        root.addView(header);
        return root;
    }

    private void showPage(Dialog dialog, LinearLayout root) {
        dialog.setContentView(root); dialog.show();
        Window window = dialog.getWindow();
        window.setBackgroundDrawable(new ColorDrawable(activity.surfaceColor()));
        window.setLayout(-1, -1);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }

    private void showAlert(AlertDialog dialog) {
        dialog.show(); activity.styleDialog(dialog); activity.attachDialogButtonHaptics(dialog);
    }

    private static final class SliderValueView extends FrameLayout {
        private final SeekBar slider;
        private final EditText value;
        private final TextView display;
        private final float factor;

        SliderValueView(MainActivity activity, SeekBar slider, EditText value, float factor) {
            super(activity);
            this.slider = slider;
            this.value = value;
            this.factor = factor;
            setFocusableInTouchMode(true);
            setDescendantFocusability(FOCUS_BEFORE_DESCENDANTS);
            slider.setSplitTrack(false);
            slider.setMinHeight(activity.dp(4));
            slider.setMaxHeight(activity.dp(4));
            GradientDrawable thumb = activity.roundRect(activity.primaryColor(), 8);
            thumb.setSize(activity.dp(16), activity.dp(16));
            slider.setThumb(thumb);
            slider.setThumbOffset(activity.dp(8));
            addView(slider, new FrameLayout.LayoutParams(-1, activity.dp(48), Gravity.BOTTOM));
            value.setTextSize(12);
            value.setTextColor(Color.TRANSPARENT);
            value.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            value.setGravity(Gravity.CENTER);
            value.setMinHeight(0);
            value.setMinWidth(0);
            value.setIncludeFontPadding(false);
            value.setPadding(activity.dp(4), 0, activity.dp(4), 0);
            value.setHint("");
            value.setBackground(activity.roundRect(activity.primaryContainerColor(), 16));
            value.setCursorVisible(false);
            addView(value, new FrameLayout.LayoutParams(activity.dp(64), activity.dp(40)));
            display = new TextView(activity);
            display.setTextSize(12);
            display.setTextColor(activity.primaryColor());
            display.setTypeface(value.getTypeface());
            display.setGravity(Gravity.CENTER);
            display.setIncludeFontPadding(false);
            display.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(display, new FrameLayout.LayoutParams(activity.dp(64), activity.dp(40)));
            value.setOnFocusChangeListener((view, focused) -> {
                GradientDrawable background = activity.roundRect(focused
                        ? activity.surfaceColor() : activity.primaryContainerColor(), 16);
                if (focused) background.setStroke(activity.dp(1), activity.primaryColor());
                value.setBackground(background);
                value.setTextColor(focused ? activity.primaryColor() : Color.TRANSPARENT);
                display.setVisibility(focused ? INVISIBLE : VISIBLE);
                value.setCursorVisible(focused);
                if (!focused) positionValue();
            });
            value.setOnEditorActionListener((view, action, event) -> {
                if (action != EditorInfo.IME_ACTION_DONE) return false;
                requestFocus();
                activity.getSystemService(InputMethodManager.class)
                        .hideSoftInputFromWindow(value.getWindowToken(), 0);
                return true;
            });
        }

        @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            positionValue();
        }

        void positionValue() {
            display.setText(BigDecimal.valueOf(slider.getProgress())
                    .divide(BigDecimal.valueOf((int) factor)).stripTrailingZeros().toPlainString());
            if (value.hasFocus()) return;
            float fraction = (slider.getProgress() - (float) slider.getMin())
                    / (slider.getMax() - (float) slider.getMin());
            if (slider.getLayoutDirection() == LAYOUT_DIRECTION_RTL) fraction = 1 - fraction;
            float center = slider.getLeft() + slider.getPaddingLeft()
                    + fraction * (slider.getWidth() - slider.getPaddingLeft() - slider.getPaddingRight());
            float inset = 2 * getResources().getDisplayMetrics().density;
            float x = Math.max(inset,
                    Math.min(getWidth() - value.getWidth() - inset, center - value.getWidth() / 2f));
            value.setTranslationX(x - value.getLeft());
            display.setTranslationX(x - display.getLeft());
        }
    }

    private static final class LayerPreviewView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private RingerVectorXml vector;
        private String message;

        LayerPreviewView(Context context) {
            super(context);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float density = getResources().getDisplayMetrics().density;
            float half = getHeight() / 2f;
            for (int i = 0; i < 2; i++) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(i == 0 ? 0xFFF4F5F6 : 0xFF202124);
                canvas.drawRect(0, i * half, getWidth(), (i + 1) * half, paint);
                int tint = i == 0 ? Color.BLACK : Color.WHITE;
                if (vector != null) {
                    float scale = Math.min((getWidth() - 8 * density) / vector.getViewportWidth(),
                            (half - 8 * density) / vector.getViewportHeight());
                    int saved = canvas.save();
                    canvas.translate((getWidth() - vector.getViewportWidth() * scale) / 2f,
                            i * half + (half - vector.getViewportHeight() * scale) / 2f);
                    canvas.scale(scale, scale);
                    vector.drawRaw(canvas, paint, tint);
                    canvas.restoreToCount(saved);
                } else if (message != null) {
                    paint.setColor(tint);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setTextAlign(Paint.Align.CENTER);
                    paint.setTextSize(10 * getResources().getDisplayMetrics().scaledDensity);
                    float width = paint.measureText(message);
                    if (width > getWidth() - 4 * density) {
                        paint.setTextSize(paint.getTextSize() * (getWidth() - 4 * density) / width);
                    }
                    canvas.drawText(message, getWidth() / 2f,
                            i * half + half / 2f - (paint.ascent() + paint.descent()) / 2f, paint);
                }
            }
        }
    }

    private static final class PreviewView extends View {
        private BatteryIconPack pack = BatteryIconLibrary.defaultPack();
        private final boolean dark, enlarged;
        private final Rect bounds = new Rect();
        private int level = 82, state;
        private boolean showNumbers = true;

        PreviewView(Context context, boolean dark, boolean enlarged) {
            super(context);
            this.dark = dark; this.enlarged = enlarged;
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (enlarged) canvas.drawColor(dark ? 0xFF202124 : 0xFFF4F5F6);
            ModuleConfig config = ModuleConfig.load(getContext());
            int tint = enlarged ? (dark ? Color.WHITE : Color.BLACK)
                    : ((MainActivity) getContext()).textColor();
            int textColor = (0.299 * Color.red(tint) + 0.587 * Color.green(tint) + 0.114 * Color.blue(tint)) >= 127.5
                    ? Color.BLACK : Color.WHITE;
            float density = getResources().getDisplayMetrics().density;
            int box = Math.round((enlarged ? 44 : 22) * density);
            int width = BatteryIconPainter.measureWidth(pack, box, state > 0);
            if (width > getWidth() - 8 * density) {
                box = Math.max(1, (int) (box * (getWidth() - 8 * density) / width));
                width = BatteryIconPainter.measureWidth(pack, box, state > 0);
            }
            int top = (getHeight() - box) / 2;
            int left = (getWidth() - width) / 2;
            bounds.set(left, top, left + width, top + box);
            BatteryIconPainter.draw(pack, canvas, bounds, level, state > 0, state > 1, state == 3,
                    tint, textColor, enlarged ? showNumbers : config.batteryLevelTextEnabled,
                    config.batteryInnerTextScalePercent / 100f,
                    SettingsStore.positionOffsetTenthDpToPx(getContext(), config.batteryIconYOffsetTenthDp),
                    SettingsStore.positionOffsetTenthDpToPx(getContext(), config.batteryBoltYOffsetTenthDp),
                    config.batteryHollowEnabled, config.batteryHollowFillFollowsLevel);
        }
    }
}
