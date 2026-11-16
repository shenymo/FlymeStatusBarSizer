package com.example.flymestatusbarsizer.ui;

import android.app.AlertDialog;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import com.example.flymestatusbarsizer.MainActivity;
import com.example.flymestatusbarsizer.config.SettingsStore;
import com.example.flymestatusbarsizer.feature.battery.BatteryIconLibrary;
import com.example.flymestatusbarsizer.feature.battery.BatteryIconPack;
import com.example.flymestatusbarsizer.feature.statusbar.BluetoothIconLibrary;
import com.example.flymestatusbarsizer.feature.statusbar.BluetoothIconState;
import com.example.flymestatusbarsizer.feature.statusbar.RingerIconLibrary;
import com.example.flymestatusbarsizer.feature.statusbar.RingerVectorXml;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class VectorIconLibraryController {
    private final MainActivity activity;
    private final String xml;
    private final RingerVectorXml vector;
    private final Runnable onSaved;

    private VectorIconLibraryController(MainActivity activity, String xml, RingerVectorXml vector,
            Runnable onSaved) {
        this.activity = activity;
        this.xml = xml;
        this.vector = vector;
        this.onSaved = onSaved;
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
        activity.setTapClickListener(row, v -> new VectorDrawableEditor(activity).showImport(
                (xml, vector, onSaved) -> new VectorIconLibraryController(activity, xml, vector, onSaved)
                        .showSaveDialog()));
        parent.addView(row);
    }

    private void showSaveDialog() {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
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
        EditText name = new EditText(activity);
        name.setHint("图标名称");
        name.setContentDescription("图标名称");
        name.setTextColor(activity.textColor());
        name.setHintTextColor(activity.subtextColor());
        name.setSingleLine(true);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
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
        try {
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
            onSaved.run();
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

    private static TextView text(MainActivity activity, String value, int size) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(activity.textColor());
        return view;
    }

    private void showAlert(AlertDialog dialog) {
        dialog.show();
        activity.styleDialog(dialog);
        activity.attachDialogButtonHaptics(dialog);
    }
}
