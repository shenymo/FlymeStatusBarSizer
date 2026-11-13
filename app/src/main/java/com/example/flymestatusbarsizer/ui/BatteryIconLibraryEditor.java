package com.example.flymestatusbarsizer.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.example.flymestatusbarsizer.MainActivity;
import com.example.flymestatusbarsizer.R;
import com.example.flymestatusbarsizer.config.ModuleConfig;
import com.example.flymestatusbarsizer.config.SettingsStore;
import com.example.flymestatusbarsizer.feature.battery.BatteryIconLibrary;
import com.example.flymestatusbarsizer.feature.battery.BatteryIconLibrary.Entry;
import com.example.flymestatusbarsizer.feature.battery.BatteryIconPack;
import com.example.flymestatusbarsizer.feature.battery.BatteryIconPainter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

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
        String[] items = entry.builtIn() ? new String[]{"复制并编辑", "复制图标包"}
                : new String[]{"修改", "复制并编辑", "重命名", "复制图标包", "删除"};
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(entry.pack().name)
                .setItems(items, (d, index) -> {
                    switch (items[index]) {
                        case "修改" -> edit(entry, false);
                        case "复制并编辑" -> edit(entry, true);
                        case "重命名" -> rename(entry);
                        case "复制图标包" -> copy(entry.pack());
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
        try { source = new JSONObject(template.json); }
        catch (JSONException impossible) { throw new IllegalStateException(impossible); }
        String id = original == null || duplicate ? UUID.randomUUID().toString() : original.id();
        Dialog dialog = new Dialog(activity);
        LinearLayout root = page(dialog, original == null || duplicate ? "新增电池图标包" : "修改电池图标包");
        ScrollView scroll = new ScrollView(activity);
        LinearLayout content = column();
        content.setPadding(activity.dp(16), activity.dp(16), activity.dp(16), activity.dp(12));
        LinearLayout previewPanel = column();
        previewPanel.setPadding(activity.dp(16), 0, activity.dp(16), 0);
        LinearLayout previewTools = new LinearLayout(activity);
        previewTools.setGravity(Gravity.CENTER_VERTICAL);
        previewPanel.addView(previewTools);
        LinearLayout previews = new LinearLayout(activity);
        PreviewView light = new PreviewView(activity, false, true);
        PreviewView dark = new PreviewView(activity, true, true);
        light.pack = dark.pack = template;
        previews.addView(light, new LinearLayout.LayoutParams(0, activity.dp(130), 1));
        previews.addView(dark, new LinearLayout.LayoutParams(0, activity.dp(130), 1));
        previewPanel.addView(previews);
        LinearLayout levelRow = new LinearLayout(activity);
        levelRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView levelLabel = text("电量 82%", 14);
        levelRow.addView(levelLabel, new LinearLayout.LayoutParams(activity.dp(80), -2));
        SeekBar level = new SeekBar(activity);
        level.setContentDescription("预览电量");
        activity.styleSeekBar(level);
        level.setMax(100);
        level.setProgress(82);
        level.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int value, boolean user) {
                light.level = dark.level = value;
                light.invalidate(); dark.invalidate(); levelLabel.setText("电量 " + value + "%");
            }
            public void onStartTrackingTouch(SeekBar bar) { }
            public void onStopTrackingTouch(SeekBar bar) { }
        });
        levelRow.addView(level, new LinearLayout.LayoutParams(0, activity.dp(48), 1));
        previewPanel.addView(levelRow);
        Spinner state = spinner(new String[]{"未充电", "已插电", "普通充电", "快充"});
        state.setContentDescription("预览充电状态");
        state.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long itemId) {
                light.state = dark.state = position; light.invalidate(); dark.invalidate();
            }
            public void onNothingSelected(AdapterView<?> parent) { }
        });
        previewTools.addView(state, new LinearLayout.LayoutParams(0, activity.dp(48), 1));
        root.addView(previewPanel);
        ImageView handle = new ImageView(activity);
        handle.setImageResource(R.drawable.ic_section_expand);
        handle.setImageTintList(ColorStateList.valueOf(activity.subtextColor()));
        handle.setScaleType(ImageView.ScaleType.CENTER);
        handle.setRotation(180);
        handle.setContentDescription("收起预览");
        activity.setTapClickListener(handle, v -> {
            boolean expanded = previewPanel.getVisibility() == View.VISIBLE;
            previewPanel.setVisibility(expanded ? View.GONE : View.VISIBLE);
            handle.setRotation(expanded ? 0 : 180);
            handle.setContentDescription(expanded ? "展开预览" : "收起预览");
        });
        root.addView(handle, new LinearLayout.LayoutParams(-1, activity.dp(48)));
        LinkedHashMap<String, EditText> fields = new LinkedHashMap<>();
        LinearLayout basic = editorSection(content, "基本信息");
        LinearLayout geometry = editorSection(content, "区域与填充");
        LinearLayout typography = editorSection(content, "数字样式");
        LinearLayout sizeRow = new LinearLayout(activity);
        String[][] labels = {{"name", "名称"}, {"width", "画布宽度"}, {"height", "画布高度"},
                {"visualBounds", "有效区域 [左,上,右,下]"}, {"fillBounds", "电芯区域 [左,上,右,下]"},
                {"textBounds", "数字区域 [左,上,右,下]"}, {"textSize", "数字基础字号"},
                {"textOutline", "数字描边宽度"}, {"textAlpha", "数字透明度 0–1"}};
        for (int i = 0; i < labels.length; i++) {
            String[] label = labels[i];
            LinearLayout fieldRow = column();
            fieldRow.setPadding(0, activity.dp(8), 0, 0);
            fieldRow.addView(text(label[1], 13));
            EditText field = input(label[1], false);
            field.setContentDescription(label[1]);
            field.setMinHeight(activity.dp(48));
            field.setText(source.optString(label[0], label[0].equals("textAlpha") ? "1" : "0"));
            if (label[0].equals("name") && (original == null || duplicate)) {
                field.setText(uniqueName(original == null ? "自定义电池"
                        : library.entries.contains(original) ? template.name + " 副本" : template.name));
            }
            fields.put(label[0], field);
            fieldRow.addView(field, new LinearLayout.LayoutParams(-1, -2));
            if (i == 1) basic.addView(sizeRow, new LinearLayout.LayoutParams(-1, -2));
            if (i == 1 || i == 2) {
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
                if (i == 1) params.rightMargin = activity.dp(12);
                sizeRow.addView(fieldRow, params);
            } else {
                LinearLayout section = i == 0 ? basic : i < 6 ? geometry : typography;
                section.addView(fieldRow, new LinearLayout.LayoutParams(-1, -2));
            }
        }
        TextView directionLabel = text("填充方向", 13);
        directionLabel.setPadding(0, activity.dp(8), 0, 0);
        geometry.addView(directionLabel);
        Spinner direction = spinner(new String[]{"从左到右", "从右到左", "从下到上", "从上到下"});
        direction.setContentDescription("填充方向");
        for (int i = 0; i < DIRECTIONS.length; i++) if (DIRECTIONS[i].equals(source.optString("fillDirection"))) direction.setSelection(i);
        geometry.addView(direction, new LinearLayout.LayoutParams(-1, activity.dp(48)));
        LinearLayout xmlSection = editorSection(content, "XML 图层");
        Spinner layer = spinner(new String[]{"外壳 XML", "电芯 XML", "空电量底图 XML（可选）", "充电 XML（可选）", "快充 XML（可选）"});
        layer.setContentDescription("XML 图层");
        xmlSection.addView(layer, new LinearLayout.LayoutParams(-1, activity.dp(48)));
        ArrayList<EditText> xmlFields = new ArrayList<>();
        for (String key : XML_KEYS) {
            EditText xml = input("XML", true);
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
        }
        layer.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long itemId) {
                for (int i = 0; i < xmlFields.size(); i++) xmlFields.get(i).setVisibility(i == position ? View.VISIBLE : View.GONE);
            }
            public void onNothingSelected(AdapterView<?> parent) { }
        });
        xmlFields.get(0).setVisibility(View.VISIBLE);
        TextView error = text("", 13);
        error.setTextColor(0xFFD32F2F);
        error.setVisibility(View.GONE);
        content.addView(error);
        addButton(previewTools, "更新预览", () -> {
            BatteryIconPack pack = readForm(fields, direction, id, error);
            if (pack != null) { light.pack = dark.pack = pack; light.invalidate(); dark.invalidate(); }
        });
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout actions = new LinearLayout(activity);
        actions.setPadding(activity.dp(12), activity.dp(8), activity.dp(12), activity.dp(12));
        for (boolean use : new boolean[]{false, true}) {
            addButton(actions, use ? "保存并使用" : "保存到库", () -> {
                BatteryIconPack pack = readForm(fields, direction, id, error);
                if (pack == null) return;
                ArrayList<Entry> entries = new ArrayList<>(library.entries);
                entries.removeIf(entry -> entry.id().equals(id));
                entries.add(new Entry(id, pack));
                persist(new BatteryIconLibrary(entries, use ? id : library.selected));
                dialog.dismiss();
                activity.showToast(use ? "已保存并选用" : "已保存到图标库");
            });
        }
        root.addView(actions);
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

    private BatteryIconPack readForm(Map<String, EditText> fields, Spinner direction, String id, TextView error) {
        try {
            JSONObject object = new JSONObject().put("version", 1).put("fillDirection", DIRECTIONS[direction.getSelectedItemPosition()]);
            for (Map.Entry<String, EditText> field : fields.entrySet()) {
                String key = field.getKey(), value = field.getValue().getText().toString().trim();
                if (key.endsWith("Bounds")) object.put(key, new JSONArray(value));
                else if (key.equals("name") || key.endsWith("Xml")) object.put(key, value);
                else object.put(key, Double.parseDouble(value));
            }
            BatteryIconPack pack = BatteryIconPack.parse(object.toString());
            if (nameExists(pack.name, id)) throw new IllegalArgumentException("同名图标包已存在，请修改名称");
            error.setVisibility(View.GONE);
            return pack;
        } catch (JSONException | IllegalArgumentException invalid) {
            error.setText(invalid.getMessage());
            error.setVisibility(View.VISIBLE);
            error.post(() -> error.requestRectangleOnScreen(new Rect(0, 0, error.getWidth(), error.getHeight()), false));
            return null;
        }
    }

    private void copy(BatteryIconPack pack) {
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("电池 XML 图标包", pack.json));
        activity.showToast("图标包已复制");
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

    private static final class PreviewView extends View {
        private BatteryIconPack pack = BatteryIconLibrary.defaultPack();
        private final boolean dark, enlarged;
        private final Rect bounds = new Rect();
        private int level = 82, state;

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
            for (int i = 0; i < (enlarged ? 2 : 1); i++) {
                int box = Math.round((i == 0 ? 22 : 66) * density);
                int width = BatteryIconPainter.measureWidth(pack, box, state > 0);
                if (width > getWidth() - 8 * density) {
                    box = Math.max(1, (int) (box * (getWidth() - 8 * density) / width));
                    width = BatteryIconPainter.measureWidth(pack, box, state > 0);
                }
                int top = enlarged ? Math.round((i == 0 ? 8 : 48) * density) : (getHeight() - box) / 2;
                int left = (getWidth() - width) / 2;
                bounds.set(left, top, left + width, top + box);
                BatteryIconPainter.draw(pack, canvas, bounds, level, state > 0, state > 1, state == 3,
                        tint, textColor, config.batteryLevelTextEnabled,
                        config.batteryInnerTextScalePercent / 100f,
                        SettingsStore.positionOffsetTenthDpToPx(getContext(), config.batteryIconYOffsetTenthDp),
                        SettingsStore.positionOffsetTenthDpToPx(getContext(), config.batteryBoltYOffsetTenthDp),
                        config.batteryHollowEnabled, config.batteryHollowFillFollowsLevel);
            }
        }
    }
}
