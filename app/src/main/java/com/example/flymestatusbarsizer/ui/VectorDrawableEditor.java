package com.example.flymestatusbarsizer.ui;

import android.app.AlertDialog;
import android.app.Dialog;
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
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import com.example.flymestatusbarsizer.MainActivity;
import com.example.flymestatusbarsizer.R;
import com.example.flymestatusbarsizer.feature.statusbar.RingerVectorXml;
import com.example.flymestatusbarsizer.feature.statusbar.VectorDrawableDocument;
import com.example.flymestatusbarsizer.feature.statusbar.VectorDrawableDocument.AttributeSpec;
import com.example.flymestatusbarsizer.feature.statusbar.VectorDrawableDocument.Kind;

import java.util.ArrayList;
import java.util.List;

public final class VectorDrawableEditor {
    @FunctionalInterface
    public interface SaveHandler {
        void save(String xml, RingerVectorXml vector, Runnable onSaved);
    }

    private final MainActivity activity;
    private VectorDrawableDocument document;
    private Dialog editorDialog;
    private LinearLayout properties;
    private ImageView preview;
    private TextView previewError;
    private EditText previewSource;
    private EditText xmlSource;
    private TextView xmlToggle;
    private String initialXml;
    private String editorTitle = "矢量图标编辑器";
    private SaveHandler onSave;
    private Runnable onCopyAiPrompt;
    private Drawable previewDrawable;
    private boolean darkPreview;
    private int previewSize = 96;
    private final Runnable previewRunnable = () -> refreshPreview(previewSource);

    public VectorDrawableEditor(MainActivity activity) {
        this.activity = activity;
    }

    public void show(String xml, String title, SaveHandler onSave, Runnable onCopyAiPrompt) {
        initialXml = xml;
        editorTitle = title;
        this.onSave = onSave;
        this.onCopyAiPrompt = onCopyAiPrompt;
        try {
            document = VectorDrawableDocument.parse(xml);
        } catch (IllegalArgumentException unsupported) {
            document = null;
        }
        showEditor();
    }

    public void showImport(SaveHandler onSave) {
        this.onSave = onSave;
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
        TextView title = text(activity, editorTitle, 20);
        title.setTypeface(null, Typeface.BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        xmlToggle = text(activity, "XML", 14);
        xmlToggle.setGravity(Gravity.CENTER);
        xmlToggle.setTextColor(activity.primaryColor());
        activity.setTapClickListener(xmlToggle, v -> toggleXml());
        header.addView(xmlToggle, new LinearLayout.LayoutParams(activity.dp(56), activity.dp(48)));
        TextView save = text(activity, "保存", 14);
        save.setGravity(Gravity.CENTER);
        save.setTextColor(activity.primaryColor());
        activity.setTapClickListener(save, v -> {
            try {
                String xml = xmlSource == null ? document.toXml() : xmlSource.getText().toString();
                RingerVectorXml vector = RingerVectorXml.parse(xml, activity);
                vector.newDrawable(activity);
                onSave.save(xml, vector, editorDialog::dismiss);
            } catch (IllegalArgumentException error) {
                previewError.setText(error.getMessage());
                if (xmlSource != null) xmlSource.setError(error.getMessage());
            }
        });
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
        editorDialog.setOnDismissListener(ignored -> {
            properties.removeCallbacks(previewRunnable);
            if (previewDrawable instanceof Animatable animation) animation.stop();
        });
        editorDialog.show();
        Window window = editorDialog.getWindow();
        window.setBackgroundDrawable(new ColorDrawable(activity.surfaceColor()));
        window.setLayout(-1, -1);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        if (document == null) toggleXml(); else rebuildProperties();
        setPreview(false);
    }

    private void toggleXml() {
        properties.removeCallbacks(previewRunnable);
        previewSource = null;
        if (xmlSource == null) {
            String source = document == null ? initialXml : document.toXml();
            properties.removeAllViews();
            if (onCopyAiPrompt != null) {
                LinearLayout xmlHeader = new LinearLayout(activity);
                xmlHeader.setGravity(Gravity.CENTER_VERTICAL);
                xmlHeader.addView(text(activity, "XML 代码", 14),
                        new LinearLayout.LayoutParams(0, -2, 1));
                TextView promptButton = text(activity, "AI 提示词", 14);
                promptButton.setGravity(Gravity.CENTER);
                promptButton.setTextColor(activity.primaryColor());
                promptButton.setPadding(activity.dp(12), activity.dp(8), activity.dp(12), activity.dp(8));
                promptButton.setBackground(activity.outlinedRect(activity.primaryContainerColor(),
                        activity.primaryColor(), 1, 8));
                Drawable copyIcon = activity.getDrawable(R.drawable.ic_content_copy_24px).mutate();
                copyIcon.setTint(activity.primaryColor());
                copyIcon.setBounds(0, 0, activity.dp(20), activity.dp(20));
                promptButton.setCompoundDrawablesRelative(copyIcon, null, null, null);
                promptButton.setCompoundDrawablePadding(activity.dp(8));
                promptButton.setContentDescription("AI 提示词，点击复制");
                activity.setTapClickListener(promptButton, v -> onCopyAiPrompt.run());
                xmlHeader.addView(promptButton, new LinearLayout.LayoutParams(-2, activity.dp(48)));
                LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(-1, -2);
                headerParams.bottomMargin = activity.dp(8);
                properties.addView(xmlHeader, headerParams);
            }
            xmlSource = input("粘贴 Android Vector XML", true);
            xmlSource.setTypeface(Typeface.MONOSPACE);
            xmlSource.setTextSize(12);
            xmlSource.setMinLines(12);
            xmlSource.setGravity(Gravity.TOP | Gravity.START);
            xmlSource.setText(source);
            xmlSource.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
                @Override public void afterTextChanged(Editable s) { schedulePreview(xmlSource); }
            });
            properties.addView(xmlSource, new LinearLayout.LayoutParams(-1, -2));
            xmlToggle.setText("图形");
        } else {
            try {
                document = VectorDrawableDocument.parse(xmlSource.getText().toString());
            } catch (IllegalArgumentException error) {
                xmlSource.setError(error.getMessage());
                return;
            }
            xmlSource = null;
            xmlToggle.setText("XML");
            rebuildProperties();
        }
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
            String xml = xmlSource == null ? document.toXml() : xmlSource.getText().toString();
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
