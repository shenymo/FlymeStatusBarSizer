package com.example.flymestatusbarsizer.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import com.example.flymestatusbarsizer.MainActivity;
import com.example.flymestatusbarsizer.R;
import com.example.flymestatusbarsizer.config.SettingsStore;
import com.example.flymestatusbarsizer.feature.battery.BatteryIconLibrary;
import com.example.flymestatusbarsizer.feature.battery.BatteryIconPack;
import com.example.flymestatusbarsizer.feature.statusbar.RingerIconLibrary;
import com.example.flymestatusbarsizer.feature.statusbar.BluetoothIconLibrary;
import com.example.flymestatusbarsizer.feature.statusbar.BluetoothIconState;
import com.example.flymestatusbarsizer.feature.statusbar.RingerVectorXml;
import com.example.flymestatusbarsizer.feature.statusbar.VectorDrawableDocument;
import com.example.flymestatusbarsizer.feature.statusbar.VectorDrawableDocument.AttributeSpec;
import com.example.flymestatusbarsizer.feature.statusbar.VectorDrawableDocument.Kind;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class VectorDrawableEditor {
    private final MainActivity activity;
    private VectorDrawableDocument document;
    private Dialog editorDialog;
    private LinearLayout properties;
    private ImageView preview;
    private TextView previewError;
    private EditText previewSource;
    private Drawable previewDrawable;
    private boolean darkPreview;
    private int previewSize = 96;
    private final Runnable previewRunnable = () -> refreshPreview(previewSource);

    public VectorDrawableEditor(MainActivity activity) {
        this.activity = activity;
    }

    public static void addSettingsRow(MainActivity activity, LinearLayout parent) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(activity.dp(60));
        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(activity, "矢量图标编辑器", 15);
        labels.addView(title);
        TextView subtitle = text(activity, "粘贴代码、修改属性并保存到图标库", 12);
        subtitle.setTextColor(activity.subtextColor());
        labels.addView(subtitle);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        TextView arrow = text(activity, "›", 25);
        arrow.setTextColor(activity.subtextColor());
        row.addView(arrow, new LinearLayout.LayoutParams(activity.dp(32), -2));
        activity.setTapClickListener(row, v -> new VectorDrawableEditor(activity).showImport());
        parent.addView(row);
    }

    private void showImport() {
        EditText input = input("粘贴 Android Vector XML", true);
        input.setTypeface(Typeface.MONOSPACE);
        input.setTextSize(12);
        input.setMinLines(12);
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null && clipboard.hasPrimaryClip()) {
            CharSequence text = clipboard.getPrimaryClip().getItemAt(0).coerceToText(activity);
            if (text != null && (text.toString().contains("<vector")
                    || text.toString().contains("<animated-vector"))) input.setText(text);
        }
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(input);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("导入矢量图标")
                .setView(scroll).setNegativeButton("取消", null)
                .setPositiveButton("解析代码", null).create();
        showAlert(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                document = VectorDrawableDocument.parse(input.getText().toString());
                dialog.dismiss();
                showEditor();
            } catch (IllegalArgumentException error) {
                input.setError(error.getMessage());
                input.requestFocus();
            }
        });
    }

    private void showEditor() {
        editorDialog = new Dialog(activity);
        editorDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = column();
        root.setBackgroundColor(activity.surfaceColor());
        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView back = new ImageView(activity);
        back.setImageResource(R.drawable.ic_settings_back);
        back.setImageTintList(ColorStateList.valueOf(activity.textColor()));
        back.setContentDescription("返回");
        activity.setTapClickListener(back, v -> editorDialog.dismiss());
        header.addView(back, new LinearLayout.LayoutParams(activity.dp(48), activity.dp(56)));
        TextView title = text(activity, "矢量图标编辑器", 20);
        title.setTypeface(null, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView save = text(activity, "保存", 14);
        save.setGravity(Gravity.CENTER);
        save.setTextColor(activity.primaryColor());
        activity.setTapClickListener(save, v -> showSaveDialog());
        header.addView(save, new LinearLayout.LayoutParams(activity.dp(64), activity.dp(48)));
        root.addView(header);

        LinearLayout previewPanel = column();
        previewPanel.setPadding(activity.dp(16), activity.dp(8), activity.dp(16), activity.dp(8));
        LinearLayout previewRow = new LinearLayout(activity);
        previewRow.setGravity(Gravity.CENTER);
        preview = new ImageView(activity);
        preview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        previewRow.addView(preview, new LinearLayout.LayoutParams(activity.dp(128), activity.dp(128)));
        previewPanel.addView(previewRow);
        LinearLayout previewTools = new LinearLayout(activity);
        previewTools.setGravity(Gravity.CENTER);
        addTool(previewTools, "浅色", () -> setPreview(false));
        addTool(previewTools, "深色", () -> setPreview(true));
        addTool(previewTools, "正常", () -> setPreviewSize(48));
        addTool(previewTools, "放大", () -> setPreviewSize(96));
        addTool(previewTools, "播放", () -> playPreview(false));
        addTool(previewTools, "重播", () -> playPreview(true));
        previewPanel.addView(previewTools);
        previewError = text(activity, "", 12);
        previewError.setTextColor(0xFFD32F2F);
        previewPanel.addView(previewError);
        root.addView(previewPanel);

        ScrollView scroll = new ScrollView(activity);
        properties = column();
        properties.setPadding(activity.dp(16), activity.dp(8), activity.dp(16), activity.dp(24));
        scroll.addView(properties);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        editorDialog.setContentView(root);
        editorDialog.show();
        Window window = editorDialog.getWindow();
        window.setBackgroundDrawable(new ColorDrawable(activity.surfaceColor()));
        window.setLayout(-1, -1);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        rebuildProperties();
        setPreview(false);
        refreshPreview(null);
    }

    private void rebuildProperties() {
        properties.removeAllViews();
        renderNode(document.root(), 0);
    }

    private void renderNode(VectorDrawableDocument.Node node, int depth) {
        LinearLayout section = column();
        section.setPadding(activity.dp(12 + depth * 8), activity.dp(10), activity.dp(12), activity.dp(8));
        section.setBackground(activity.roundRect(activity.surfaceSoftColor(), 8));
        String heading = nodeTitle(node);
        TextView title = text(activity, heading, 14);
        title.setTextColor(activity.primaryColor());
        title.setTypeface(null, Typeface.BOLD);
        section.addView(title);
        for (AttributeSpec spec : VectorDrawableDocument.specsFor(node.tag())) {
            if (node.has(spec.name())) addAttributeRow(section, node, spec);
        }
        List<AttributeSpec> missing = missing(node);
        if (!missing.isEmpty()) {
            TextView add = text(activity, "+ 添加属性", 13);
            add.setTextColor(activity.primaryColor());
            add.setGravity(Gravity.CENTER_VERTICAL);
            add.setMinHeight(activity.dp(42));
            activity.setTapClickListener(add, v -> showAddAttribute(node, missing));
            section.addView(add);
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = activity.dp(8);
        properties.addView(section, params);
        for (VectorDrawableDocument.Node child : node.children()) renderNode(child, depth + 1);
    }

    private void addAttributeRow(LinearLayout parent, VectorDrawableDocument.Node node,
            AttributeSpec spec) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, activity.dp(3), 0, activity.dp(3));
        TextView label = text(activity, spec.label() + "\n" + spec.name(), 12);
        label.setTextColor(activity.subtextColor());
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setMaxLines(2);
        row.addView(label, new LinearLayout.LayoutParams(activity.dp(112), activity.dp(48)));
        String value = node.get(spec.name());
        if (spec.kind() == Kind.ENUM || spec.kind() == Kind.BOOLEAN) {
            String[] values = spec.kind() == Kind.BOOLEAN ? new String[]{"false", "true"} : spec.choices();
            Spinner spinner = spinner(values);
            for (int i = 0; i < values.length; i++) if (values[i].equals(value)) spinner.setSelection(i);
            spinner.setContentDescription(spec.label());
            spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int position, long id) {
                    node.set(spec.name(), values[position]);
                    schedulePreview(null);
                }
                @Override public void onNothingSelected(android.widget.AdapterView<?> p) { }
            });
            row.addView(spinner, new LinearLayout.LayoutParams(0, activity.dp(48), 1));
        } else if (spec.kind() == Kind.COLOR) {
            TextView color = text(activity, value, 13);
            color.setGravity(Gravity.CENTER);
            color.setContentDescription(spec.label());
            updateColorSwatch(color, value);
            activity.setTapClickListener(color, v -> showColorDialog(node, spec, color));
            row.addView(color, new LinearLayout.LayoutParams(0, activity.dp(44), 1));
        } else if ("pathData".equals(spec.name())) {
            TextView path = text(activity, summarize(value), 12);
            path.setTypeface(Typeface.MONOSPACE);
            path.setGravity(Gravity.CENTER_VERTICAL);
            path.setContentDescription(spec.label());
            path.setMaxLines(2);
            activity.setTapClickListener(path, v -> showPathDialog(node, spec));
            row.addView(path, new LinearLayout.LayoutParams(0, activity.dp(48), 1));
        } else {
            EditText input = input(spec.label(), false);
            input.setText(value);
            input.setSelectAllOnFocus(true);
            input.setContentDescription(spec.label());
            if (spec.kind() == Kind.NUMBER) {
                input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                        | InputType.TYPE_NUMBER_FLAG_SIGNED);
            }
            input.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
                @Override public void afterTextChanged(Editable s) {
                    node.set(spec.name(), s.toString().trim());
                    schedulePreview(input);
                }
            });
            row.addView(input, new LinearLayout.LayoutParams(0, -2, 1));
        }
        TextView remove = text(activity, "删除", 12);
        remove.setTextColor(0xFFD32F2F);
        remove.setGravity(Gravity.CENTER);
        remove.setMinHeight(activity.dp(42));
        remove.setContentDescription("删除" + spec.label());
        activity.setTapClickListener(remove, v -> {
            node.remove(spec.name());
            rebuildProperties();
            schedulePreview(null);
        });
        row.addView(remove, new LinearLayout.LayoutParams(activity.dp(44), activity.dp(42)));
        parent.addView(row);
    }

    private void showAddAttribute(VectorDrawableDocument.Node node, List<AttributeSpec> missing) {
        String[] labels = new String[missing.size()];
        for (int i = 0; i < labels.length; i++) {
            AttributeSpec spec = missing.get(i);
            labels[i] = spec.label() + "  (" + spec.name() + ")";
        }
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("添加属性")
                .setItems(labels, (d, which) -> {
                    AttributeSpec spec = missing.get(which);
                    node.set(spec.name(), spec.defaultValue());
                    rebuildProperties();
                    schedulePreview(null);
                }).setNegativeButton("取消", null).create();
        showAlert(dialog);
    }

    private void showPathDialog(VectorDrawableDocument.Node node, AttributeSpec spec) {
        EditText input = input("路径数据", true);
        input.setTypeface(Typeface.MONOSPACE);
        input.setMinLines(8);
        input.setText(node.get(spec.name()));
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(spec.label())
                .setView(input).setNegativeButton("取消", null).setPositiveButton("应用", null).create();
        showAlert(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            node.set(spec.name(), input.getText().toString().trim());
            dialog.dismiss();
            rebuildProperties();
            schedulePreview(null);
        });
    }

    private void showColorDialog(VectorDrawableDocument.Node node, AttributeSpec spec, TextView swatch) {
        EditText input = input("颜色，例如 #FFFFFFFF", false);
        input.setText(node.get(spec.name()));
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(spec.label())
                .setView(input).setNegativeButton("取消", null).setPositiveButton("应用", null).create();
        showAlert(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                String color = input.getText().toString().trim();
                Color.parseColor(color);
                node.set(spec.name(), color);
                updateColorSwatch(swatch, color);
                dialog.dismiss();
                schedulePreview(null);
            } catch (IllegalArgumentException error) {
                input.setError("请输入合法颜色，例如 #FFFFFFFF");
            }
        });
    }

    private void schedulePreview(EditText source) {
        if (properties == null) return;
        properties.removeCallbacks(previewRunnable);
        previewSource = source;
        properties.postDelayed(previewRunnable, 220);
    }

    private void refreshPreview(EditText source) {
        try {
            String xml = document.toXml();
            RingerVectorXml vector = RingerVectorXml.parse(xml, activity);
            if (previewDrawable instanceof Animatable animation) animation.stop();
            previewDrawable = vector.newDrawable(activity);
            preview.setImageDrawable(previewDrawable);
            if (previewDrawable instanceof Animatable animation) animation.start();
            preview.setImageTintList(ColorStateList.valueOf(darkPreview ? Color.WHITE : Color.BLACK));
            preview.getLayoutParams().width = activity.dp(previewSize);
            preview.getLayoutParams().height = activity.dp(previewSize);
            preview.requestLayout();
            previewError.setText("");
            if (source != null) source.setError(null);
        } catch (IllegalArgumentException error) {
            if (source != null) source.setError(error.getMessage());
            previewError.setText(error.getMessage());
        }
    }

    private void showSaveDialog() {
        LinearLayout content = column();
        content.setPadding(activity.dp(20), activity.dp(8), activity.dp(20), 0);
        RadioGroup target = new RadioGroup(activity);
        RadioButton vibrate = radio("震动图标库", 1);
        RadioButton silent = radio("静音图标库", 2);
        RadioButton battery = radio("电池图标库", 3);
        RadioButton bluetooth = radio("蓝牙图标包", 4);
        target.addView(vibrate); target.addView(silent); target.addView(battery);
        target.addView(bluetooth); target.check(1);
        content.addView(target);
        RadioGroup mode = new RadioGroup(activity);
        RadioButton create = radio("新建图标", 10);
        RadioButton replace = radio("覆盖已有图标", 11);
        mode.addView(create); mode.addView(replace); mode.check(10);
        content.addView(mode);
        EditText name = input("图标名称", false);
        name.setText("自定义矢量图标");
        content.addView(name);
        Spinner entrySpinner = spinner(new String[]{"新建条目"});
        Spinner layerSpinner = spinner(new String[]{"外壳", "电芯", "空电量背景", "充电", "快充"});
        content.addView(entrySpinner);
        content.addView(layerSpinner);
        layerSpinner.setVisibility(View.GONE);
        List<RingerIconLibrary.Entry> ringerEntries = new ArrayList<>();
        List<BatteryIconLibrary.Entry> batteryEntries = new ArrayList<>();
        List<BluetoothIconLibrary.Pack> bluetoothPacks = new ArrayList<>();
        String[] states = new String[BluetoothIconState.values().length];
        for (BluetoothIconState state : BluetoothIconState.values()) states[state.ordinal()] = state.title;
        Spinner bluetoothState = spinner(states);
        content.addView(bluetoothState);
        TextView addBluetoothPack = text(activity, "添加图标包", 14);
        addBluetoothPack.setGravity(Gravity.CENTER);
        addBluetoothPack.setMinHeight(activity.dp(48));
        addBluetoothPack.setTextColor(activity.primaryColor());
        content.addView(addBluetoothPack);
        Runnable refreshOptions = () -> {
            boolean isBattery = target.getCheckedRadioButtonId() == 3;
            boolean isBluetooth = target.getCheckedRadioButtonId() == 4;
            layerSpinner.setVisibility(isBattery ? View.VISIBLE : View.GONE);
            bluetoothState.setVisibility(isBluetooth ? View.VISIBLE : View.GONE);
            mode.setVisibility(isBluetooth ? View.GONE : View.VISIBLE);
            name.setVisibility(isBluetooth ? View.GONE : View.VISIBLE);
            entrySpinner.setVisibility(isBattery || isBluetooth || mode.getCheckedRadioButtonId() == 11
                    ? View.VISIBLE : View.GONE);
            ArrayList<String> labels = new ArrayList<>();
            if (isBattery) {
                batteryEntries.clear();
                for (BatteryIconLibrary.Entry item : BatteryIconLibrary.load(activity.prefs()).entries) {
                    batteryEntries.add(item); labels.add(item.pack().name);
                }
            } else if (target.getCheckedRadioButtonId() == 4) {
                bluetoothPacks.clear();
                for (BluetoothIconLibrary.Pack pack : BluetoothIconLibrary.load(activity.prefs(), activity).packs()) {
                    if (!pack.builtIn()) {
                        bluetoothPacks.add(pack); labels.add(pack.name());
                    }
                }
            } else {
                ringerEntries.clear();
                boolean silentTarget = target.getCheckedRadioButtonId() == 2;
                for (RingerIconLibrary.Entry item : RingerIconLibrary.load(activity.prefs(), silentTarget).entries) {
                    if (!item.id().startsWith("builtin:") && item.vector() != null) {
                        ringerEntries.add(item); labels.add(item.name());
                    }
                }
            }
            addBluetoothPack.setVisibility(isBluetooth && bluetoothPacks.isEmpty() ? View.VISIBLE : View.GONE);
            if (labels.isEmpty()) labels.add(isBluetooth ? "请先添加图标包"
                    : isBattery ? "请选择电池图标包" : "没有可覆盖图标");
            setSpinnerItems(entrySpinner, labels);
            replace.setEnabled(isBluetooth ? !bluetoothPacks.isEmpty() : !labels.isEmpty() && !(labels.size() == 1
                    && (labels.get(0).startsWith("没有") || labels.get(0).startsWith("请选择"))));
            if (!replace.isEnabled() && mode.getCheckedRadioButtonId() == 11) mode.check(10);
        };
        activity.setTapClickListener(addBluetoothPack, v -> BluetoothIconLibraryEditor.createPack(activity, pack -> {
            refreshOptions.run();
            for (int i = 0; i < bluetoothPacks.size(); i++) {
                if (bluetoothPacks.get(i).id().equals(pack.id())) entrySpinner.setSelection(i);
            }
        }));
        target.setOnCheckedChangeListener((group, checkedId) -> refreshOptions.run());
        mode.setOnCheckedChangeListener((group, checkedId) -> refreshOptions.run());
        refreshOptions.run();
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("保存到图标库")
                .setView(content).setNegativeButton("取消", null).setPositiveButton("保存", null).create();
        showAlert(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> saveDocument(
                dialog, target, mode, name, entrySpinner, layerSpinner, ringerEntries,
                batteryEntries, bluetoothPacks, bluetoothState, false));
    }

    private void saveDocument(AlertDialog dialog, RadioGroup target, RadioGroup mode, EditText name,
            Spinner entrySpinner, Spinner layerSpinner, List<RingerIconLibrary.Entry> ringerEntries,
            List<BatteryIconLibrary.Entry> batteryEntries,
            List<BluetoothIconLibrary.Pack> bluetoothPacks, Spinner bluetoothState, boolean confirmed) {
        String title = name.getText().toString().trim();
        boolean isBluetooth = target.getCheckedRadioButtonId() == 4;
        if (!isBluetooth && title.isEmpty()) { name.setError("请输入图标名称"); return; }
        if (isBluetooth && bluetoothPacks.isEmpty()) { activity.showToast("请先添加图标包"); return; }
        boolean replacing = isBluetooth || mode.getCheckedRadioButtonId() == 11;
        if (replacing && !confirmed) {
            AlertDialog confirm = new AlertDialog.Builder(activity).setTitle("确认覆盖？")
                    .setMessage(isBluetooth ? "替换「" + bluetoothPacks.get(entrySpinner.getSelectedItemPosition()).name()
                            + "」中的「" + BluetoothIconState.values()[bluetoothState.getSelectedItemPosition()].title + "」图标？"
                            : "保存后会替换所选图标的内容。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("覆盖", (d, which) -> saveDocument(dialog, target, mode, name,
                            entrySpinner, layerSpinner, ringerEntries, batteryEntries,
                            bluetoothPacks, bluetoothState, true)).create();
            showAlert(confirm);
            return;
        }
        final String xml = document.toXml();
        try {
            RingerVectorXml vector = RingerVectorXml.parse(xml, activity);
            if (target.getCheckedRadioButtonId() == 3) {
                BatteryIconLibrary library = BatteryIconLibrary.load(activity.prefs());
                int position = Math.max(0, entrySpinner.getSelectedItemPosition());
                BatteryIconLibrary.Entry base = batteryEntries.isEmpty()
                        ? library.selectedEntry() : batteryEntries.get(Math.min(position, batteryEntries.size() - 1));
                String[] keys = {"shellXml", "fillXml", "backgroundXml", "chargingXml", "quickChargingXml"};
                JSONObject object = new JSONObject(base.pack().json).put("name", title)
                        .put(keys[Math.max(0, layerSpinner.getSelectedItemPosition())], xml);
                BatteryIconPack pack = BatteryIconPack.parse(object.toString());
                ensureBatteryName(library, title, replacing ? base.id() : "");
                ArrayList<BatteryIconLibrary.Entry> entries = new ArrayList<>(library.entries);
                String id = replacing ? base.id() : UUID.randomUUID().toString();
                entries.removeIf(item -> item.id().equals(id));
                entries.add(new BatteryIconLibrary.Entry(id, pack));
                persistBattery(new BatteryIconLibrary(entries, library.selected));
            } else if (target.getCheckedRadioButtonId() == 4) {
                BluetoothIconLibrary library = BluetoothIconLibrary.load(activity.prefs(), activity);
                BluetoothIconLibrary.Pack pack = library.pack(bluetoothPacks.get(entrySpinner.getSelectedItemPosition()).id());
                BluetoothIconState state = BluetoothIconState.values()[bluetoothState.getSelectedItemPosition()];
                vector.newDrawable(activity);
                activity.prefs().edit().putString(SettingsStore.KEY_STATUS_BAR_BLUETOOTH_ICON_PACKS,
                        library.withPack(pack.withIcon(state, new BluetoothIconLibrary.Icon(xml, vector))).encode()).apply();
                SettingsStore.notifyChanged(activity);
            } else {
                boolean silentTarget = target.getCheckedRadioButtonId() == 2;
                RingerIconLibrary library = RingerIconLibrary.load(activity.prefs(), silentTarget);
                RingerIconLibrary.Entry base = null;
                if (replacing && !ringerEntries.isEmpty()) {
                    base = ringerEntries.get(Math.min(entrySpinner.getSelectedItemPosition(), ringerEntries.size() - 1));
                }
                ensureRingerName(library, title, base == null ? "" : base.id());
                String id = base == null ? UUID.randomUUID().toString() : base.id();
                RingerIconLibrary.Entry saved = new RingerIconLibrary.Entry(id, title, xml, vector, null);
                ArrayList<RingerIconLibrary.Entry> entries = new ArrayList<>(library.entries);
                if (base != null) entries.set(entries.indexOf(base), saved); else entries.add(1, saved);
                activity.prefs().edit().putString(RingerIconLibrary.key(silentTarget),
                        new RingerIconLibrary(entries, library.selected).encode()).apply();
                SettingsStore.notifyChanged(activity);
            }
            dialog.dismiss();
            if (editorDialog != null) editorDialog.dismiss();
            activity.showToast(isBluetooth ? "已保存到图标包" : "已保存到图标库");
        } catch (IllegalArgumentException | JSONException error) {
            if (isBluetooth) activity.showToast(error.getMessage());
            else name.setError(error.getMessage());
        }
    }

    private void persistBattery(BatteryIconLibrary library) {
        activity.prefs().edit().putString(SettingsStore.KEY_BATTERY_ICON_LIBRARY, library.encode()).apply();
        SettingsStore.notifyChanged(activity);
    }

    private void ensureRingerName(RingerIconLibrary library, String name, String ownId) {
        if (name.equals("内置矢量图标") || library.entries.stream()
                .anyMatch(item -> !item.id().equals(ownId) && item.name().equals(name))) {
            throw new IllegalArgumentException("同名图标已存在，请换一个名称");
        }
    }

    private void ensureBatteryName(BatteryIconLibrary library, String name, String ownId) {
        if (library.entries.stream().anyMatch(item -> !item.id().equals(ownId)
                && item.pack().name.equals(name))) {
            throw new IllegalArgumentException("同名图标包已存在，请换一个名称");
        }
    }

    private List<AttributeSpec> missing(VectorDrawableDocument.Node node) {
        ArrayList<AttributeSpec> result = new ArrayList<>();
        for (AttributeSpec spec : VectorDrawableDocument.specsFor(node.tag())) {
            if (!node.has(spec.name())) result.add(spec);
        }
        return result;
    }

    private String nodeTitle(VectorDrawableDocument.Node node) {
        String name = node.get("name");
        String base = switch (node.tag()) {
            case "vector" -> "画布基础";
            case "group" -> "图层组";
            case "path" -> "路径图层";
            case "clip-path" -> "裁剪路径";
            case "animated-vector" -> "动画矢量";
            case "target" -> "动画目标";
            case "objectAnimator" -> "属性动画";
            case "set" -> "动画集合";
            case "propertyValuesHolder" -> "属性值集合";
            case "keyframe" -> "关键帧";
            case "aapt:attr" -> "渐变属性";
            case "gradient" -> "渐变";
            case "item" -> "渐变色标";
            default -> node.tag();
        };
        return name == null || name.isBlank() ? base : base + " · " + name;
    }

    private void setPreview(boolean dark) {
        darkPreview = dark;
        preview.setBackground(activity.roundRect(dark ? 0xFF202124 : 0xFFF4F5F6, 8));
        refreshPreview(null);
    }

    private void setPreviewSize(int size) {
        previewSize = size;
        refreshPreview(null);
    }

    private void playPreview(boolean restart) {
        if (previewDrawable instanceof Animatable animation) {
            if (restart) animation.stop();
            animation.start();
        }
    }

    private void addTool(LinearLayout parent, String label, Runnable action) {
        TextView view = text(activity, label, 12);
        view.setGravity(Gravity.CENTER);
        view.setTextColor(activity.primaryColor());
        view.setMinHeight(activity.dp(36));
        activity.setTapClickListener(view, v -> action.run());
        parent.addView(view, new LinearLayout.LayoutParams(0, activity.dp(36), 1));
    }

    private void updateColorSwatch(TextView view, String value) {
        try {
            int color = Color.parseColor(value);
            view.setBackground(activity.outlinedRect(color | 0xFF000000, activity.strokeColor(), 1, 8));
            view.setTextColor(Color.luminance(color | 0xFF000000) > .5f ? Color.BLACK : Color.WHITE);
            view.setText(value);
        } catch (IllegalArgumentException error) {
            view.setText(value);
        }
    }

    private Spinner spinner(String[] values) {
        Spinner spinner = new Spinner(activity);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        return spinner;
    }

    private void setSpinnerItems(Spinner spinner, List<String> values) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private RadioButton radio(String label, int id) {
        RadioButton button = new RadioButton(activity);
        button.setId(id);
        button.setText(label);
        button.setTextColor(activity.textColor());
        return button;
    }

    private EditText input(String hint, boolean multiline) {
        EditText input = new EditText(activity);
        input.setHint(hint);
        input.setContentDescription(hint);
        input.setTextColor(activity.textColor());
        input.setHintTextColor(activity.subtextColor());
        input.setSingleLine(!multiline);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | (multiline ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        return input;
    }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(activity);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private static TextView text(MainActivity activity, String value, int size) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(activity.textColor());
        return view;
    }

    private String summarize(String value) {
        return value.length() > 80 ? value.substring(0, 77) + "..." : value;
    }

    private void showAlert(AlertDialog dialog) {
        dialog.show();
        activity.styleDialog(dialog);
        activity.attachDialogButtonHaptics(dialog);
    }
}
