package com.example.flymestatusbarsizer.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.example.flymestatusbarsizer.MainActivity;
import com.example.flymestatusbarsizer.R;
import com.example.flymestatusbarsizer.config.SettingsStore;
import com.example.flymestatusbarsizer.feature.statusbar.BluetoothIconLibrary;
import com.example.flymestatusbarsizer.feature.statusbar.BluetoothIconState;
import com.example.flymestatusbarsizer.feature.statusbar.RingerVectorXml;

import java.util.ArrayList;
import java.util.UUID;

public final class BluetoothIconLibraryEditor {
    private final MainActivity activity;
    private final Runnable onChanged;
    private BluetoothIconLibrary library;
    private LinearLayout libraryList;
    private LinearLayout stateList;

    private BluetoothIconLibraryEditor(MainActivity activity, Runnable onChanged) {
        this.activity = activity;
        this.onChanged = onChanged;
        library = BluetoothIconLibrary.load(activity.prefs(), activity);
    }

    public static void addSettingsRows(MainActivity activity, LinearLayout root) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(activity.dp(56));
        TextView title = new TextView(activity);
        title.setText("蓝牙图标包");
        title.setTextColor(activity.textColor());
        title.setTextSize(15);
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView preview = new ImageView(activity);
        row.addView(preview, new LinearLayout.LayoutParams(activity.dp(36), activity.dp(36)));
        TextView arrow = new TextView(activity);
        arrow.setText("›");
        arrow.setTextColor(activity.subtextColor());
        arrow.setTextSize(25);
        row.addView(arrow, new LinearLayout.LayoutParams(activity.dp(28), -2));
        Runnable refresh = () -> {
            BluetoothIconLibrary current = BluetoothIconLibrary.load(activity.prefs(), activity);
            preview.setImageDrawable(current.newDrawable(activity, BluetoothIconState.CONNECTED));
            preview.setImageTintList(ColorStateList.valueOf(activity.textColor()));
        };
        refresh.run();
        activity.setTapClickListener(row, v -> new BluetoothIconLibraryEditor(activity, refresh).showPack());
        root.addView(row);
    }

    private void showPack() {
        Dialog dialog = new Dialog(activity);
        LinearLayout root = page(dialog, "蓝牙图标包");
        TextView manage = button("图标库", this::showLibrary);
        root.addView(manage, new LinearLayout.LayoutParams(-1, activity.dp(48)));
        ScrollView scroll = new ScrollView(activity);
        stateList = column();
        stateList.setPadding(activity.dp(16), activity.dp(8), activity.dp(16), activity.dp(24));
        renderStates();
        scroll.addView(stateList);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        showPage(dialog, root);
    }

    private void renderStates() {
        stateList.removeAllViews();
        for (BluetoothIconState state : BluetoothIconState.values()) {
            BluetoothIconLibrary.Entry entry = library.selectedEntry(state);
            LinearLayout row = new LinearLayout(activity);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(activity.dp(60));
            TextView title = text(state.title, 15);
            row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
            ImageView preview = image(library.newDrawable(activity, state), activity.textColor());
            row.addView(preview, new LinearLayout.LayoutParams(activity.dp(36), activity.dp(36)));
            TextView name = text(entry.name() + "  ›", 13);
            name.setTextColor(activity.subtextColor());
            name.setMaxWidth(activity.dp(140));
            name.setMaxLines(2);
            row.addView(name);
            activity.setTapClickListener(row, v -> showSelection(state));
            stateList.addView(row);
        }
    }

    private void showSelection(BluetoothIconState state) {
        Dialog dialog = new Dialog(activity);
        LinearLayout root = page(dialog, state.title);
        ScrollView scroll = new ScrollView(activity);
        LinearLayout choices = column();
        choices.setPadding(activity.dp(16), activity.dp(8), activity.dp(16), activity.dp(24));
        for (BluetoothIconLibrary.Entry entry : library.entries()) {
            LinearLayout row = entryRow(entry);
            if (library.selectedId(state).equals(entry.id())) {
                row.setBackground(activity.roundRect(activity.primaryContainerColor(), 8));
                row.addView(text("✓", 20), new LinearLayout.LayoutParams(activity.dp(28), -2));
            }
            row.setEnabled(entry.vector() != null);
            activity.setTapClickListener(row, v -> {
                if (entry.vector() == null) return;
                try {
                    entry.vector().newDrawable(activity);
                    persist(library.withSelection(state, entry.id()));
                    dialog.dismiss();
                } catch (IllegalArgumentException invalid) {
                    activity.showToast(invalid.getMessage());
                }
            });
            choices.addView(row);
        }
        scroll.addView(choices);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        showPage(dialog, root);
    }

    private void showLibrary() {
        Dialog dialog = new Dialog(activity);
        LinearLayout root = page(dialog, "蓝牙图标库");
        root.addView(button("新增图标", () -> edit(null)),
                new LinearLayout.LayoutParams(-1, activity.dp(48)));
        ScrollView scroll = new ScrollView(activity);
        libraryList = column();
        libraryList.setPadding(activity.dp(16), activity.dp(8), activity.dp(16), activity.dp(24));
        renderLibrary();
        scroll.addView(libraryList);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        dialog.setOnDismissListener(ignored -> libraryList = null);
        showPage(dialog, root);
    }

    private void renderLibrary() {
        libraryList.removeAllViews();
        for (BluetoothIconLibrary.Entry entry : library.entries()) {
            LinearLayout row = entryRow(entry);
            TextView more = text("⋮", 24);
            more.setGravity(Gravity.CENTER);
            more.setContentDescription(entry.name() + "，更多操作");
            row.addView(more, new LinearLayout.LayoutParams(activity.dp(48), activity.dp(48)));
            Runnable menu = () -> {
                AlertDialog options = new AlertDialog.Builder(activity).setTitle(entry.name())
                        .setItems(entry.builtIn() ? new String[]{"复制并修改"}
                                : new String[]{"修改代码", "删除"}, (d, which) -> {
                            if (which == 0) edit(entry);
                            else {
                                ArrayList<BluetoothIconLibrary.Entry> entries = new ArrayList<>(library.entries());
                                entries.removeIf(item -> item.id().equals(entry.id()));
                                persist(library.withEntries(entries));
                            }
                        }).create();
                options.show();
                activity.styleDialog(options);
                activity.attachDialogButtonHaptics(options);
            };
            activity.setTapClickListener(more, v -> menu.run());
            activity.setTapClickListener(row, v -> edit(entry));
            libraryList.addView(row);
        }
    }

    private LinearLayout entryRow(BluetoothIconLibrary.Entry entry) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(activity.dp(10), activity.dp(8), activity.dp(8), activity.dp(8));
        row.setMinimumHeight(activity.dp(64));
        Drawable icon;
        try {
            icon = entry.vector() == null ? null : entry.vector().newDrawable(activity);
        } catch (IllegalArgumentException invalid) {
            icon = null;
        }
        row.addView(image(icon, activity.textColor()),
                new LinearLayout.LayoutParams(activity.dp(40), activity.dp(40)));
        TextView name = text(entry.name() + (icon == null ? "\nXML 无法解析" : ""), 15);
        name.setPadding(activity.dp(10), 0, 0, 0);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }

    private void edit(BluetoothIconLibrary.Entry original) {
        Dialog dialog = new Dialog(activity);
        LinearLayout root = page(dialog, original == null ? "新增图标"
                : original.builtIn() ? "复制图标" : "修改图标");
        ScrollView scroll = new ScrollView(activity);
        LinearLayout content = column();
        content.setPadding(activity.dp(20), activity.dp(8), activity.dp(20), activity.dp(16));
        LinearLayout preview = new LinearLayout(activity);
        preview.setGravity(Gravity.CENTER);
        content.addView(preview, new LinearLayout.LayoutParams(-1, activity.dp(108)));
        EditText name = input("图标名称", false);
        if (original != null) name.setText(original.name() + (original.builtIn() ? " 副本" : ""));
        content.addView(name);
        EditText xml = input("粘贴 Android Vector XML", true);
        xml.setTypeface(Typeface.MONOSPACE);
        xml.setTextSize(12);
        xml.setMinLines(12);
        xml.setGravity(Gravity.TOP | Gravity.START);
        if (original != null) xml.setText(original.xml());
        content.addView(xml);
        Runnable showPreview = () -> {
            try {
                RingerVectorXml vector = RingerVectorXml.parse(xml.getText().toString(), activity);
                Drawable light = vector.newDrawable(activity);
                Drawable dark = vector.newDrawable(activity);
                preview.removeAllViews();
                for (boolean darkBackground : new boolean[]{false, true}) {
                    LinearLayout tile = new LinearLayout(activity);
                    tile.setGravity(Gravity.CENTER);
                    tile.setBackground(activity.roundRect(darkBackground ? 0xFF202124 : 0xFFF4F5F6, 8));
                    tile.addView(image(darkBackground ? dark : light, darkBackground ? Color.WHITE : Color.BLACK),
                            new LinearLayout.LayoutParams(activity.dp(72), activity.dp(72)));
                    preview.addView(tile, new LinearLayout.LayoutParams(0, -1, 1));
                }
                xml.setError(null);
            } catch (IllegalArgumentException invalid) {
                xml.setError(invalid.getMessage());
            }
        };
        content.addView(button("生成预览", showPreview), new LinearLayout.LayoutParams(-1, activity.dp(48)));
        if (original != null) showPreview.run();
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(button("保存到图标库", () -> {
            String title = name.getText().toString().trim();
            if (title.isEmpty()) { name.setError("请输入图标名称"); return; }
            boolean replacing = original != null && !original.builtIn();
            String id = replacing ? original.id() : UUID.randomUUID().toString();
            if (library.entries().stream().anyMatch(entry -> !entry.id().equals(id)
                    && entry.name().equals(title))) {
                name.setError("同名图标已存在");
                return;
            }
            try {
                String source = xml.getText().toString();
                RingerVectorXml vector = RingerVectorXml.parse(source, activity);
                vector.newDrawable(activity);
                BluetoothIconLibrary.Entry entry = new BluetoothIconLibrary.Entry(id, title, source, vector, null);
                ArrayList<BluetoothIconLibrary.Entry> entries = new ArrayList<>(library.entries());
                if (replacing) entries.set(entries.indexOf(original), entry);
                else entries.add(entry);
                persist(library.withEntries(entries));
                dialog.dismiss();
                activity.showToast("已保存到图标库");
            } catch (IllegalArgumentException invalid) {
                xml.setError(invalid.getMessage());
                xml.requestFocus();
            }
        }), new LinearLayout.LayoutParams(-1, activity.dp(48)));
        showPage(dialog, root);
    }

    private ImageView image(Drawable drawable, int color) {
        ImageView image = new ImageView(activity);
        image.setImageDrawable(drawable);
        image.setImageTintList(ColorStateList.valueOf(color));
        if (drawable instanceof Animatable animation) {
            image.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View view) { animation.start(); }
                @Override public void onViewDetachedFromWindow(View view) { animation.stop(); }
            });
        }
        return image;
    }

    private void persist(BluetoothIconLibrary updated) {
        library = updated;
        activity.prefs().edit().putString(SettingsStore.KEY_STATUS_BAR_BLUETOOTH_ICON_LIBRARY,
                library.encode()).apply();
        SettingsStore.notifyChanged(activity);
        if (stateList != null) renderStates();
        if (libraryList != null) renderLibrary();
        onChanged.run();
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView text(String value, int size) {
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(activity.textColor());
        return text;
    }

    private TextView button(String value, Runnable action) {
        TextView button = text(value, 14);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(activity.dp(48));
        button.setBackground(activity.roundRect(activity.primaryContainerColor(), 8));
        button.setTextColor(activity.primaryColor());
        activity.setTapClickListener(button, v -> action.run());
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
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(header);
        return root;
    }

    private void showPage(Dialog dialog, LinearLayout root) {
        dialog.setContentView(root);
        dialog.show();
        Window window = dialog.getWindow();
        window.setBackgroundDrawable(new ColorDrawable(activity.surfaceColor()));
        window.setLayout(-1, -1);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }
}
