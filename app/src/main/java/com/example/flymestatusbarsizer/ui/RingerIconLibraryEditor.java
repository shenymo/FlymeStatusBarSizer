package com.example.flymestatusbarsizer.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import com.example.flymestatusbarsizer.MainActivity;
import com.example.flymestatusbarsizer.R;
import com.example.flymestatusbarsizer.config.SettingsStore;
import com.example.flymestatusbarsizer.feature.statusbar.RingerIconLibrary;
import com.example.flymestatusbarsizer.feature.statusbar.RingerIconLibrary.Entry;
import com.example.flymestatusbarsizer.feature.statusbar.RingerIconVectorHooks;
import com.example.flymestatusbarsizer.feature.statusbar.RingerVectorXml;
import com.example.flymestatusbarsizer.util.OnlineIconLibraryClient;

import java.util.ArrayList;
import java.util.UUID;

public final class RingerIconLibraryEditor {
    private final MainActivity activity;
    private final RingerIconLibrary.Target target;
    private final Runnable onChanged;
    private RingerIconLibrary library;
    private LinearLayout list;

    private RingerIconLibraryEditor(MainActivity activity, RingerIconLibrary.Target target,
            Runnable onChanged) {
        this.activity = activity;
        this.target = target;
        this.onChanged = onChanged;
        library = RingerIconLibrary.load(activity.prefs(), target);
    }

    public static void addSettingsRows(MainActivity activity, LinearLayout root) {
        for (RingerIconLibrary.Target target : RingerIconLibrary.Target.values()) {
            LinearLayout row = new LinearLayout(activity);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(activity.dp(56));
            TextView title = new TextView(activity);
            title.setText(target.title);
            title.setTextColor(activity.textColor());
            title.setTextSize(15);
            row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
            ImageView preview = new ImageView(activity);
            row.addView(preview, new LinearLayout.LayoutParams(activity.dp(36), activity.dp(36)));
            TextView name = new TextView(activity);
            name.setTextColor(activity.subtextColor());
            name.setTextSize(13);
            name.setMaxWidth(activity.dp(140));
            name.setMaxLines(2);
            row.addView(name);
            Runnable refresh = () -> {
                Entry entry = RingerIconLibrary.load(activity.prefs(), target).selectedEntry();
                name.setText(entry == null ? "内置矢量图标  ›" : entry.name() + "  ›");
                Drawable drawable = RingerIconVectorHooks.builtInPreview(activity, target);
                if (entry != null && entry.vector() != null) {
                    try {
                        drawable = entry.vector().newDrawable(activity);
                    } catch (IllegalArgumentException invalid) {
                        name.setText("内置矢量图标  ›");
                    }
                }
                preview.setImageDrawable(drawable);
                preview.setImageTintList(ColorStateList.valueOf(activity.textColor()));
            };
            refresh.run();
            activity.setTapClickListener(row, v -> new RingerIconLibraryEditor(activity, target, refresh).show());
            root.addView(row);
        }
    }

    private void show() {
        Dialog dialog = new Dialog(activity);
        LinearLayout root = page(dialog, target.title + "库");
        OnlineIconLibraryDialog.addEntry(activity, root, this::showOnlineLibrary);
        FrameLayout body = new FrameLayout(activity);
        ScrollView scroll = new ScrollView(activity);
        list = column();
        list.setPadding(activity.dp(16), activity.dp(8), activity.dp(16), activity.dp(88));
        scroll.addView(list);
        body.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        TextView add = button("＋", () -> edit(null));
        add.setTextSize(30);
        add.setContentDescription("新增图标");
        FrameLayout.LayoutParams addParams = new FrameLayout.LayoutParams(activity.dp(56), activity.dp(56), Gravity.BOTTOM | Gravity.END);
        addParams.setMargins(activity.dp(20), activity.dp(20), activity.dp(20), activity.dp(20));
        body.addView(add, addParams);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        renderList();
        showPage(dialog, root);
    }

    private void renderList() {
        list.removeAllViews();
        addLibraryRow(null);
        for (Entry entry : library.entries) addLibraryRow(entry);
    }

    private void addLibraryRow(Entry entry) {
        String id = entry == null ? "" : entry.id();
        boolean selected = library.selected.equals(id);
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(activity.dp(10), activity.dp(8), 0, activity.dp(8));
        row.setMinimumHeight(activity.dp(68));
        row.setBackground(activity.roundRect(selected ? activity.primaryContainerColor() : activity.surfaceSoftColor(), 8));
        ImageView icon = new ImageView(activity);
        icon.setImageDrawable(drawable(entry));
        icon.setImageTintList(ColorStateList.valueOf(activity.textColor()));
        row.addView(icon, new LinearLayout.LayoutParams(activity.dp(44), activity.dp(44)));
        TextView name = text(entry == null ? "内置矢量图标" : entry.name(), 15);
        name.setPadding(activity.dp(10), 0, activity.dp(8), 0);
        if (entry != null && entry.vector() == null) name.setText(entry.name() + "\n代码需要修改");
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        if (selected) {
            TextView check = text("✓", 20);
            check.setTextColor(activity.primaryColor());
            check.setContentDescription("当前选用");
            row.addView(check, new LinearLayout.LayoutParams(activity.dp(28), -2));
        }
        activity.setTapClickListener(row, v -> {
            if (entry != null && entry.vector() == null) { edit(entry); return; }
            persist(new RingerIconLibrary(library.entries, id));
        });
        if (entry != null && !entry.id().startsWith("builtin:")) {
            TextView more = text("⋮", 24);
            more.setGravity(Gravity.CENTER);
            more.setContentDescription(entry.name() + "，更多操作");
            activity.setTapClickListener(more, v -> showMenu(entry));
            row.addView(more, new LinearLayout.LayoutParams(activity.dp(48), activity.dp(48)));
        } else row.setPadding(activity.dp(10), activity.dp(8), activity.dp(16), activity.dp(8));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = activity.dp(8);
        list.addView(row, params);
    }

    private void showMenu(Entry entry) {
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(entry.name())
                .setItems(new String[]{"重命名", "编辑图标", "删除"}, (d, which) -> {
                    if (which == 0) rename(entry);
                    else if (which == 1) edit(entry);
                    else {
                        Runnable remove = () -> {
                            ArrayList<Entry> items = new ArrayList<>(library.entries);
                            items.removeIf(item -> item.id().equals(entry.id()));
                            persist(new RingerIconLibrary(items, library.selected.equals(entry.id()) ? "" : library.selected));
                        };
                        if (library.selected.equals(entry.id())) {
                            AlertDialog confirm = new AlertDialog.Builder(activity).setTitle("删除图标？")
                                    .setMessage("该图标正在使用，删除后恢复内置矢量图标。")
                                    .setNegativeButton("取消", null).setPositiveButton("删除", (a, b) -> remove.run()).create();
                            showAlert(confirm);
                        } else remove.run();
                    }
                }).create();
        showAlert(dialog);
    }

    private void rename(Entry entry) {
        EditText input = input("名称", false);
        input.setText(entry.name());
        LinearLayout content = column();
        content.setPadding(activity.dp(20), activity.dp(8), activity.dp(20), 0);
        content.addView(input);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("重命名")
                .setView(content).setNegativeButton("取消", null).setPositiveButton("保存", null).create();
        showAlert(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (!validateName(input, name, entry.id())) return;
            ArrayList<Entry> items = new ArrayList<>(library.entries);
            items.set(items.indexOf(entry), new Entry(entry.id(), name, entry.xml(), entry.vector(), entry.error()));
            persist(new RingerIconLibrary(items, library.selected));
            dialog.dismiss();
        });
    }

    private void edit(Entry original) {
        new VectorDrawableEditor(activity).show(
                original == null ? RingerIconLibrary.TRIANGLE_XML : original.xml(),
                original == null ? "新增图标" : original.name(),
                (xml, vector, onSaved) -> showSaveDialog(original, xml, vector, onSaved),
                this::copyAiPrompt);
    }

    private void showSaveDialog(Entry original, String xml, RingerVectorXml vector, Runnable onSaved) {
        LinearLayout content = column();
        content.setPadding(activity.dp(20), activity.dp(8), activity.dp(20), 0);
        EditText name = input("图标名称", false);
        content.addView(name);
        RadioGroup mode = new RadioGroup(activity);
        mode.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton copy = new RadioButton(activity);
        copy.setId(View.generateViewId());
        copy.setText("另存新图标");
        copy.setTextColor(activity.textColor());
        RadioButton replace = new RadioButton(activity);
        replace.setId(View.generateViewId());
        replace.setText("替换原图标");
        replace.setTextColor(activity.textColor());
        if (original != null) {
            mode.addView(copy);
            mode.addView(replace);
            mode.check(copy.getId());
            content.addView(mode);
            String copyName = uniqueName(original.name() + " 副本");
            name.setText(copyName);
            mode.setOnCheckedChangeListener((group, checked) -> {
                name.setText(checked == replace.getId() ? original.name() : copyName);
                name.setError(null);
            });
        }
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("保存图标")
                .setView(content).setNegativeButton("取消", null)
                .setNeutralButton("保存到库", null).setPositiveButton("保存并使用", null).create();
        showAlert(dialog);
        for (boolean use : new boolean[]{false, true}) {
            dialog.getButton(use ? AlertDialog.BUTTON_POSITIVE : AlertDialog.BUTTON_NEUTRAL)
                    .setOnClickListener(v -> {
                        boolean replacing = original != null && mode.getCheckedRadioButtonId() == replace.getId();
                        String title = name.getText().toString().trim();
                        if (!validateName(name, title, replacing ? original.id() : "")) return;
                        String id = replacing ? original.id() : UUID.randomUUID().toString();
                        Entry entry = new Entry(id, title, xml, vector, null);
                        ArrayList<Entry> entries = new ArrayList<>(library.entries);
                        if (replacing) entries.set(entries.indexOf(original), entry);
                        else entries.add(1, entry);
                        persist(new RingerIconLibrary(entries, use ? id : library.selected));
                        dialog.dismiss();
                        onSaved.run();
                        activity.showToast(use ? "已保存并选用" : "已保存到图标库");
                    });
        }
    }

    private void copyAiPrompt() {
        String prompt = "请基于下面的 Android Vector XML 模板，修改出一个图标。\n"
                + "图标图案描述：【在此输入图案描述，例如："
                + target.title + "】\n\n"
                + "请优先只修改 pathData；如果图案由多个部分组成，可以增加 path，"
                + "但保留 vector 根标签、尺寸和视口设置。\n"
                + "只输出最终的纯 XML 代码，不要 Markdown、代码块标记、前缀、后缀或解释文字。\n"
                + "图形用于状态栏单色显示，不要额外预留安全留白。保持宽高比等比放大并居中，"
                + "让图形较长方向铺满 24×24 视口，外沿到达坐标 0 和 24，不要缩成 18×18 或强行拉伸变形。"
                + "若有描边，以描边外沿为边界，不得超出视口。\n"
                + "下方三角形路径仅为 XML 结构示例，不要沿用其留白；请根据目标图案重新调整路径坐标，充分利用整个视口。\n"
                + "不要使用 SVG、动画、渐变、@color/、?attr/ 或其他外部资源引用。\n\n"
                + "模板：\n"
                + RingerIconLibrary.TRIANGLE_XML;
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(
                android.content.Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Android Vector XML 提示词", prompt));
            activity.showToast("AI 提示词已复制");
        }
    }

    private boolean validateName(EditText input, String name, String ownId) {
        if (name.isEmpty()) { input.setError("请输入图标名称"); input.requestFocus(); return false; }
        if (nameExists(name, ownId)) { input.setError("同名图标已存在，请换一个名称"); input.requestFocus(); return false; }
        return true;
    }

    private boolean nameExists(String name, String ownId) {
        return name.equals("内置矢量图标") || library.entries.stream().anyMatch(entry -> !entry.id().equals(ownId) && entry.name().equals(name));
    }

    private void persist(RingerIconLibrary updated) {
        library = updated;
        activity.prefs().edit().putString(RingerIconLibrary.key(target), library.encode()).apply();
        SettingsStore.notifyChanged(activity);
        renderList();
        onChanged.run();
    }

    private void showOnlineLibrary() {
        OnlineIconLibraryDialog.show(activity,
                target.onlineType,
                "在线" + target.title,
                () -> {
                    return library.entries.stream().filter(entry -> !entry.id().startsWith("builtin:"))
                            .map(entry -> new OnlineIconLibraryDialog.UploadData(entry.name(), entry.xml()))
                            .toList();
                },
                icon -> {
                    if (library.entries.stream().anyMatch(entry -> entry.id().equals("online:" + icon.id()))) {
                        throw new IllegalArgumentException("该图标已经下载");
                    }
                    RingerVectorXml vector = RingerVectorXml.parse(icon.payload());
                    String name = uniqueName(icon.name());
                    ArrayList<Entry> entries = new ArrayList<>(library.entries);
                    entries.add(1, new Entry("online:" + icon.id(), name, icon.payload(), vector, null));
                    persist(new RingerIconLibrary(entries, library.selected));
                    return "已下载到本地图标库";
                });
    }

    private String uniqueName(String base) {
        String name = base;
        for (int suffix = 2; nameExists(name, ""); suffix++) name = base + " " + suffix;
        return name;
    }

    private Drawable drawable(Entry entry) {
        if (entry == null) return RingerIconVectorHooks.builtInPreview(activity, target);
        if (entry.vector() == null) return activity.getDrawable(R.drawable.ic_settings_tune);
        try {
            return entry.vector().newDrawable(activity);
        } catch (IllegalArgumentException invalid) {
            return RingerIconVectorHooks.builtInPreview(activity, target);
        }
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
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }

    private void showAlert(AlertDialog dialog) {
        dialog.show();
        activity.styleDialog(dialog);
        activity.attachDialogButtonHaptics(dialog);
    }
}
