package com.example.flymestatusbarsizer.feature.statusbar;

import android.app.AlertDialog;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.media.session.PlaybackState;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.example.flymestatusbarsizer.MainActivity;
import com.example.flymestatusbarsizer.config.SettingsStore;
import com.example.flymestatusbarsizer.feature.statusbar.StatusBarPageLayout.Item;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class StatusBarPageEditor {
    private final MainActivity activity;
    private final ArrayList<Item> left;
    private final ArrayList<Item> right;
    private final LinearLayout content;
    private final LinearLayout lists;
    private final StatusBarPageView preview;
    private final FrameLayout previewFrame;

    public StatusBarPageEditor(MainActivity activity) {
        this.activity = activity;
        StatusBarPageLayout layout = read();
        left = new ArrayList<>(layout.left);
        right = new ArrayList<>(layout.right);
        content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        lists = new LinearLayout(activity);
        lists.setOrientation(LinearLayout.VERTICAL);
        preview = new StatusBarPageView(activity, item -> { });
        previewFrame = new FrameLayout(activity);
        previewFrame.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        View camera = new View(activity);
        GradientDrawable cameraShape = new GradientDrawable();
        cameraShape.setShape(GradientDrawable.OVAL);
        cameraShape.setColor(activity.textColor());
        camera.setBackground(cameraShape);
        previewFrame.addView(camera, new FrameLayout.LayoutParams(activity.dp(10),
                activity.dp(10), Gravity.CENTER));
        activity.addSwitchRow(content, "状态栏分页", "左滑查看第二页，右滑返回。仅解锁后生效。",
                SettingsStore.KEY_STATUS_BAR_PAGES_ENABLED, false,
                (button, checked) -> lists.setVisibility(checked ? View.VISIBLE : View.GONE));
        lists.setVisibility(SettingsStore.readBoolean(activity.prefs(),
                SettingsStore.KEY_STATUS_BAR_PAGES_ENABLED, false) ? View.VISIBLE : View.GONE);
        preview.setStyle(activity.textColor(), activity.dp(12), Typeface.DEFAULT);
        preview.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int gap = activity.dp(14);
            preview.setSideBounds(new Rect(activity.dp(8), 0, (r - l) / 2 - gap, b - t),
                    new Rect((r - l) / 2 + gap, 0, r - l - activity.dp(8), b - t));
        });
        content.addView(lists, activity.matchWrap());
        rebuild();
    }

    public View buildCard() {
        return activity.buildSectionCard("状态栏分页", "", content);
    }

    private StatusBarPageLayout read() {
        return StatusBarPageLayout.parse(SettingsStore.readString(activity.prefs(),
                        SettingsStore.KEY_STATUS_BAR_PAGE_LEFT, StatusBarPageLayout.DEFAULT_LEFT),
                SettingsStore.readString(activity.prefs(), SettingsStore.KEY_STATUS_BAR_PAGE_RIGHT,
                        StatusBarPageLayout.DEFAULT_RIGHT));
    }

    private void rebuild() {
        lists.removeAllViews();
        lists.addView(previewFrame, new LinearLayout.LayoutParams(-1, activity.dp(40)));
        preview.setItems(read());
        preview.setText(Item.DATE, "11/17 周二");
        preview.setText(Item.TEMPERATURE, "35.2℃");
        preview.setText(Item.POWER, "12.3W");
        preview.setText(Item.MEMORY, "RAM 6.2G");
        preview.setMedia("当前歌曲", true, PlaybackState.ACTION_SKIP_TO_PREVIOUS
                | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT, true);
        addSide(left, right, "左侧显示", "移至右侧");
        addSide(right, left, "右侧显示", "移至左侧");
    }

    private void addSide(ArrayList<Item> side, ArrayList<Item> other, String label, String moveLabel) {
        activity.addProfileSectionHeader(lists, label, "");
        for (int i = 0; i < side.size(); i++) {
            int index = i;
            Item item = side.get(i);
            TextView row = new TextView(activity);
            row.setText(item.label + "   ⋮");
            row.setTextSize(15);
            row.setTextColor(activity.textColor());
            row.setPadding(activity.dp(16), activity.dp(12), activity.dp(16), activity.dp(12));
            row.setOnClickListener(v -> {
                List<String> options = new ArrayList<>();
                if (index > 0) options.add("上移");
                if (index + 1 < side.size()) options.add("下移");
                options.add(moveLabel);
                options.add("移除");
                new AlertDialog.Builder(activity).setTitle(item.label)
                        .setItems(options.toArray(new String[0]), (dialog, which) -> {
                            String selected = options.get(which);
                            if (selected.equals("上移")) Collections.swap(side, index, index - 1);
                            else if (selected.equals("下移")) Collections.swap(side, index, index + 1);
                            else {
                                side.remove(index);
                                if (selected.equals(moveLabel)) other.add(item);
                            }
                            save();
                        }).show();
            });
            lists.addView(row, activity.matchWrap());
        }
        List<Item> available = new ArrayList<>();
        for (Item item : Item.values()) if (!left.contains(item) && !right.contains(item)) available.add(item);
        if (!available.isEmpty()) {
            activity.addActionButtonRow(lists, "添加项目", "", "添加", () -> {
                String[] labels = new String[available.size()];
                for (int i = 0; i < labels.length; i++) labels[i] = available.get(i).label;
                new AlertDialog.Builder(activity).setTitle(label).setItems(labels, (dialog, which) -> {
                    side.add(available.get(which));
                    save();
                }).show();
            });
        }
    }

    private void save() {
        activity.prefs().edit()
                .putString(SettingsStore.KEY_STATUS_BAR_PAGE_LEFT, StatusBarPageLayout.encode(left))
                .putString(SettingsStore.KEY_STATUS_BAR_PAGE_RIGHT, StatusBarPageLayout.encode(right)).apply();
        SettingsStore.notifyChanged(activity);
        rebuild();
    }
}
