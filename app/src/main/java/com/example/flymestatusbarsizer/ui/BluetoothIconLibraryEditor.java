package com.example.flymestatusbarsizer.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.InputType;
import android.text.TextUtils;
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

import java.util.function.Consumer;
import java.util.UUID;

public final class BluetoothIconLibraryEditor {
    private final MainActivity activity;
    private final Runnable onChanged;
    private BluetoothIconLibrary library;
    private LinearLayout packList;
    private LinearLayout stateList;
    private String editingPack;
    private TextView enablePack;

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
        TextView name = new TextView(activity);
        name.setTextColor(activity.subtextColor());
        name.setTextSize(13);
        name.setMaxWidth(activity.dp(140));
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(name);
        TextView arrow = new TextView(activity);
        arrow.setText("›");
        arrow.setTextColor(activity.subtextColor());
        arrow.setTextSize(25);
        row.addView(arrow, new LinearLayout.LayoutParams(activity.dp(28), -2));
        Runnable refresh = () -> {
            BluetoothIconLibrary current = BluetoothIconLibrary.load(activity.prefs(), activity);
            preview.setImageDrawable(current.newDrawable(activity, BluetoothIconState.CONNECTED));
            preview.setImageTintList(ColorStateList.valueOf(activity.textColor()));
            name.setText(current.selectedPack().name());
        };
        refresh.run();
        activity.setTapClickListener(row, v -> new BluetoothIconLibraryEditor(activity, refresh).show());
        root.addView(row);
    }

    public static void createPack(MainActivity activity, Consumer<BluetoothIconLibrary.Pack> onCreated) {
        BluetoothIconLibraryEditor editor = new BluetoothIconLibraryEditor(activity, () -> {});
        editor.namePack(BluetoothIconLibrary.defaults().selectedPack(), true, onCreated);
    }

    private void show() {
        Dialog dialog = new Dialog(activity);
        LinearLayout root = page(dialog, "蓝牙图标包");
        root.addView(button("添加图标包", () -> namePack(BluetoothIconLibrary.defaults().selectedPack(),
                true, this::showPack)), new LinearLayout.LayoutParams(-1, activity.dp(48)));
        ScrollView scroll = new ScrollView(activity);
        packList = column();
        packList.setPadding(activity.dp(16), activity.dp(8), activity.dp(16), activity.dp(24));
        renderPacks();
        scroll.addView(packList);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        showPage(dialog, root);
    }

    private void renderPacks() {
        packList.removeAllViews();
        for (BluetoothIconLibrary.Pack pack : library.packs()) {
            boolean selected = library.selectedId().equals(pack.id());
            LinearLayout row = new LinearLayout(activity);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(activity.dp(8), activity.dp(6), 0, activity.dp(6));
            row.setBackground(activity.roundRect(selected ? activity.primaryContainerColor()
                    : activity.surfaceSoftColor(), 8));
            row.addView(image(pack.newDrawable(activity, BluetoothIconState.CONNECTED), activity.textColor()),
                    new LinearLayout.LayoutParams(activity.dp(44), activity.dp(48)));
            TextView name = text(pack.name() + (selected ? "  ✓" : ""), 15);
            row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            activity.setTapClickListener(row, v -> persist(library.withSelection(pack.id())));
            if (!pack.builtIn()) {
                TextView edit = text("编辑", 14);
                edit.setGravity(Gravity.CENTER);
                edit.setTextColor(activity.primaryColor());
                edit.setContentDescription("编辑" + pack.name());
                activity.setTapClickListener(edit, v -> showPack(pack));
                row.addView(edit, new LinearLayout.LayoutParams(activity.dp(56), activity.dp(48)));
            }
            TextView more = text("⋮", 24);
            more.setGravity(Gravity.CENTER);
            more.setContentDescription(pack.name() + "，更多操作");
            activity.setTapClickListener(more, v -> menu(pack));
            row.addView(more, new LinearLayout.LayoutParams(activity.dp(48), activity.dp(48)));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.bottomMargin = activity.dp(8);
            packList.addView(row, params);
        }
    }

    private void menu(BluetoothIconLibrary.Pack pack) {
        String[] items = pack.builtIn() ? new String[]{"查看", "复制并编辑"}
                : new String[]{"复制并编辑", "重命名", "删除"};
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(pack.name())
                .setItems(items, (d, index) -> {
                    switch (items[index]) {
                        case "查看" -> showPack(pack);
                        case "复制并编辑" -> namePack(pack, true, this::showPack);
                        case "重命名" -> namePack(pack, false, ignored -> {});
                        case "删除" -> {
                            if (library.selectedId().equals(pack.id())) {
                                showAlert(new AlertDialog.Builder(activity).setTitle("删除图标包？")
                                        .setMessage("删除后使用内置图标包。")
                                        .setNegativeButton("取消", null)
                                        .setPositiveButton("删除", (a, b) -> persist(library.withoutPack(pack.id()))).create());
                            } else persist(library.withoutPack(pack.id()));
                        }
                    }
                }).create();
        showAlert(dialog);
    }

    private void namePack(BluetoothIconLibrary.Pack source, boolean copy,
            Consumer<BluetoothIconLibrary.Pack> onSaved) {
        EditText name = input("图标包名称", false);
        String base = copy ? source.builtIn() ? "自定义图标包" : source.name() + " 副本" : source.name();
        String suggestion = base;
        if (copy) {
            for (int suffix = 2; nameExists(suggestion, ""); suffix++) suggestion = base + " " + suffix;
        }
        name.setText(suggestion);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(copy ? "添加图标包" : "重命名图标包")
                .setView(name).setNegativeButton("取消", null).setPositiveButton("保存", null).create();
        showAlert(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = name.getText().toString().trim();
            if (title.isEmpty()) { name.setError("请输入图标包名称"); return; }
            String id = copy ? UUID.randomUUID().toString() : source.id();
            if (nameExists(title, id)) { name.setError("同名图标包已存在"); return; }
            BluetoothIconLibrary.Pack pack = new BluetoothIconLibrary.Pack(id, title, source.icons());
            persist(library.withPack(pack));
            dialog.dismiss();
            onSaved.accept(pack);
        });
    }

    private boolean nameExists(String name, String ownId) {
        return library.packs().stream().anyMatch(pack -> !pack.id().equals(ownId) && pack.name().equals(name));
    }

    private void showPack(BluetoothIconLibrary.Pack pack) {
        editingPack = pack.id();
        Dialog dialog = new Dialog(activity);
        LinearLayout root = page(dialog, pack.name());
        enablePack = text("", 14);
        enablePack.setGravity(Gravity.CENTER);
        enablePack.setTextColor(activity.primaryColor());
        activity.setTapClickListener(enablePack, v -> persist(library.withSelection(pack.id())));
        ((LinearLayout) root.getChildAt(0)).addView(enablePack,
                new LinearLayout.LayoutParams(activity.dp(80), activity.dp(48)));
        ScrollView scroll = new ScrollView(activity);
        stateList = column();
        stateList.setPadding(activity.dp(16), activity.dp(8), activity.dp(16), activity.dp(24));
        renderStates();
        scroll.addView(stateList);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        dialog.setOnDismissListener(ignored -> { stateList = null; editingPack = null; enablePack = null; });
        showPage(dialog, root);
    }

    private void renderStates() {
        stateList.removeAllViews();
        BluetoothIconLibrary.Pack pack = library.pack(editingPack);
        boolean selected = library.selectedId().equals(pack.id());
        enablePack.setText(selected ? "使用中" : "启用");
        enablePack.setEnabled(!selected);
        for (BluetoothIconState state : BluetoothIconState.values()) {
            LinearLayout row = new LinearLayout(activity);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(activity.dp(60));
            row.addView(text(state.title, 15), new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(image(pack.newDrawable(activity, state), activity.textColor()),
                    new LinearLayout.LayoutParams(activity.dp(40), activity.dp(40)));
            if (!pack.builtIn()) {
                TextView arrow = text("›", 25);
                arrow.setGravity(Gravity.CENTER);
                row.addView(arrow, new LinearLayout.LayoutParams(activity.dp(28), -2));
                activity.setTapClickListener(row, v -> new VectorDrawableEditor(activity).show(
                        pack.icons().get(state).xml(), pack.name() + " · " + state.title,
                        (xml, vector) -> persist(library.withPack(library.pack(pack.id()).withIcon(state,
                                new BluetoothIconLibrary.Icon(xml, vector))))));
            }
            stateList.addView(row);
        }
    }

    private void showAlert(AlertDialog dialog) {
        dialog.show();
        activity.styleDialog(dialog);
        activity.attachDialogButtonHaptics(dialog);
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
        activity.prefs().edit().putString(SettingsStore.KEY_STATUS_BAR_BLUETOOTH_ICON_PACKS,
                library.encode()).apply();
        SettingsStore.notifyChanged(activity);
        if (stateList != null) renderStates();
        if (packList != null) renderPacks();
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
        heading.setSingleLine(true);
        heading.setEllipsize(TextUtils.TruncateAt.END);
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
