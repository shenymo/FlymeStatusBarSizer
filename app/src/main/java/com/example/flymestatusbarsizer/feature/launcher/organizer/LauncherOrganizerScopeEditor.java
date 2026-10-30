package com.example.flymestatusbarsizer.feature.launcher.organizer;

import com.example.flymestatusbarsizer.MainActivity;
import com.example.flymestatusbarsizer.ui.PageViewUtils;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** The preview and launcher use the same effective selection; dialogs edit a copy. */
final class LauncherOrganizerScopeEditor {
    private final MainActivity activity;
    private final LinearLayout root;
    private final Consumer<LauncherOrganizerScope> changed;
    private final Map<String, Drawable.ConstantState> icons = new HashMap<>();
    private JSONObject desktop;
    private LauncherOrganizerScope scope;
    private Integer currentScreen;

    LauncherOrganizerScopeEditor(MainActivity activity, LinearLayout root, Consumer<LauncherOrganizerScope> changed) {
        this.activity = activity;
        this.root = root;
        this.changed = changed;
    }

    void render(JSONObject desktop, LauncherOrganizerScope scope) {
        if (this.desktop != desktop) icons.clear();
        this.desktop = desktop;
        this.scope = scope;
        root.removeAllViews();
        if (desktop == null) {
            return;
        }
        try {
            label(root, summary(desktop, scope));
            button(root, "快捷选择：" + LauncherOrganizerScope.MODES[scope.mode] + " ▾", this::choosePreset);
            toggle(root, "保留首屏（含空白位置）", scope.keepFirstScreen, value -> {
                LauncherOrganizerScope next = copy();
                next.keepFirstScreen = value;
                changed.accept(next);
            });
            renderPage();
            List<JSONObject> newApps = apps(-1, true);
            if (!newApps.isEmpty()) button(root, "未放到桌面的应用（已选 " + selectedCount(newApps, selectedIds(scope))
                    + "/" + newApps.size() + "）", () -> chooseApps("添加到桌面", newApps, false));
        } catch (Exception error) { label(root, error.getMessage()); }
    }

    static String summary(JSONObject desktop, LauncherOrganizerScope scope) throws Exception {
        JSONArray selected = scope.selectedApps(desktop);
        int existing = 0;
        int selectedExisting = 0;
        JSONArray apps = desktop.getJSONArray("apps");
        for (int i = 0; i < apps.length(); i++) if (!apps.getJSONObject(i).getBoolean("newApp")) existing++;
        for (int i = 0; i < selected.length(); i++) if (!selected.getJSONObject(i).getBoolean("newApp")) selectedExisting++;
        return "已勾选 " + selectedExisting + " 个桌面应用，添加 " + (selected.length() - selectedExisting) + " 个应用\n"
                + "未选 " + (existing - selectedExisting) + " 个桌面应用，整页保留 " + scope.protectedScreens(desktop).size() + " 页。";
    }

    private void choosePreset() {
        new AlertDialog.Builder(activity).setTitle("快捷选择（保留锁定页面）")
                .setSingleChoiceItems(LauncherOrganizerScope.MODES, scope.mode, (dialog, which) -> {
                    try {
                        LauncherOrganizerScope next = copy();
                        if (which == LauncherOrganizerScope.MANUAL) next.editSelection(desktop, Set.of(), false);
                        else {
                            next.mode = which;
                            next.keptApps.clear();
                            next.keptFolders.clear();
                            next.selectedAppIds.clear();
                            next.includeNewApps = false;
                        }
                        changed.accept(next);
                        dialog.dismiss();
                    } catch (Exception error) { showError(error); }
                }).setNegativeButton("取消", null).show();
    }

    private void renderPage() throws Exception {
        JSONArray screens = desktop.getJSONArray("screens");
        if (screens.length() == 0) return;
        int page = 0;
        for (int i = 0; i < screens.length(); i++) if (currentScreen != null && screens.getInt(i) == currentScreen) page = i;
        currentScreen = screens.getInt(page);
        final int index = page;
        LinearLayout navigation = row(root);
        TextView previous = button(navigation, "‹", () -> switchPage(index - 1));
        previous.setContentDescription("上一页");
        previous.setEnabled(page > 0);
        button(navigation, "第 " + (page + 1) + " / " + screens.length() + " 页 ▾", () -> {
            String[] titles = new String[screens.length()];
            for (int i = 0; i < titles.length; i++) titles[i] = "第 " + (i + 1) + " 页";
            new AlertDialog.Builder(activity).setTitle("选择桌面页面")
                    .setSingleChoiceItems(titles, index, (dialog, which) -> { dialog.dismiss(); switchPage(which); })
                    .setNegativeButton("取消", null).show();
        });
        TextView following = button(navigation, "›", () -> switchPage(index + 1));
        following.setContentDescription("下一页");
        following.setEnabled(page < screens.length() - 1);
        boolean locked = scope.protectedScreens(desktop).contains(currentScreen);
        if (page != 0) toggle(root, "保留本页（含空白位置）", locked, value -> {
            LauncherOrganizerScope next = copy();
            if (value) next.keptScreens.add(currentScreen);
            else next.keptScreens.remove(currentScreen);
            changed.accept(next);
        });
        List<JSONObject> pageApps = apps(currentScreen, false);
        Set<String> selected = selectedIds(scope);
        label(root, "本页已选 " + selectedCount(pageApps, selected) + "/" + pageApps.size() + " 个应用");
        LinearLayout actions = row(root);
        button(actions, "全选本页", () -> select(pageApps, true)).setEnabled(!locked && !pageApps.isEmpty());
        button(actions, "清空本页", () -> select(pageApps, false)).setEnabled(!locked && !pageApps.isEmpty());

        JSONArray items = desktop.optJSONArray("layoutItems");
        if (items == null) {
            label(root, "请重新读取桌面以显示布局。");
            return;
        }
        LauncherOrganizerDesktopGrid grid = new LauncherOrganizerDesktopGrid(activity, desktop.getInt("columns"), desktop.getInt("rows"));
        grid.setBackground(activity.roundRect(activity.surfaceSoftColor(), 12));
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            if (item.getInt("container") != -100 || item.getInt("screen") != currentScreen) continue;
            View tile = itemTile(item, selected, locked);
            grid.addItem(tile, item.getInt("cellX"), item.getInt("cellY"), item.getInt("spanX"), item.getInt("spanY"));
        }
        root.addView(grid, activity.matchWrapWithTop(8));
    }

    private View itemTile(JSONObject item, Set<String> selected, boolean locked) throws Exception {
        String id = item.getString("id");
        JSONArray all = desktop.getJSONArray("apps");
        for (int i = 0; i < all.length(); i++) {
            JSONObject app = all.getJSONObject(i);
            if (!app.getBoolean("newApp") && app.getString("id").equals(id)) {
                return appTile(app, selected.contains(id), !locked, value -> select(List.of(app), value));
            }
        }
        JSONArray folders = desktop.getJSONArray("folders");
        for (int i = 0; i < folders.length(); i++) {
            JSONObject folder = folders.getJSONObject(i);
            if (!Integer.toString(folder.getInt("id")).equals(id)) continue;
            List<JSONObject> members = folderApps(folder.getInt("id"));
            int count = selectedCount(members, selected);
            String name = folder.getString("name");
            return tile(name, null, members, count + "/" + members.size(), count > 0, !locked && !members.isEmpty(),
                    value -> select(members, count != members.size()), () -> chooseApps(name, members, true));
        }
        String name = item.optString("name", "");
        int type = item.getInt("itemType");
        if (name.isEmpty()) name = type == 4 || type == 5 ? "小组件" : type == 2 ? "文件夹" : "快捷方式";
        TextView placeholder = text(name + "\n保留", 12);
        placeholder.setGravity(Gravity.CENTER);
        placeholder.setBackground(activity.outlinedRect(activity.surfaceSoftColor(), activity.strokeColor(), 1, 10));
        placeholder.setAlpha(0.55f);
        return placeholder;
    }

    private void switchPage(int page) {
        try {
            JSONArray screens = desktop.getJSONArray("screens");
            if (page < 0 || page >= screens.length()) return;
            currentScreen = screens.getInt(page);
            render(desktop, scope);
        } catch (Exception error) { showError(error); }
    }

    private void chooseApps(String title, List<JSONObject> apps, boolean folder) {
        try {
            LauncherOrganizerScope next = copy();
            Set<String> selected = selectedIds(next);
            LinearLayout content = new LinearLayout(activity);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(activity.dp(16), activity.dp(8), activity.dp(16), activity.dp(8));
            TextView count = text("", 14);
            content.addView(count);
            Runnable updateCount = () -> count.setText("已选 " + selectedCount(apps, selected) + "/" + apps.size() + " 个应用");
            updateCount.run();
            LinearLayout actions = row(content);
            List<CheckBox> boxes = new ArrayList<>();
            button(actions, "全选", () -> { for (CheckBox box : boxes) box.setChecked(true); });
            button(actions, "清空", () -> { for (CheckBox box : boxes) box.setChecked(false); });
            int columns = 3;
            LauncherOrganizerDesktopGrid grid = new LauncherOrganizerDesktopGrid(activity, columns, (apps.size() + columns - 1) / columns);
            for (int i = 0; i < apps.size(); i++) {
                JSONObject app = apps.get(i);
                String id = app.getString("id");
                FrameLayout tile = appTile(app, selected.contains(id), true, value -> {
                    if (value) selected.add(id); else selected.remove(id);
                    updateCount.run();
                });
                boxes.add((CheckBox) tile.findViewWithTag("selection"));
                grid.addItem(tile, i % columns, i / columns, 1, 1);
            }
            content.addView(grid, PageViewUtils.matchWrap());
            ScrollView scroll = new ScrollView(activity);
            scroll.addView(content);
            new AlertDialog.Builder(activity).setTitle(title).setView(scroll)
                    .setNegativeButton("取消", null)
                    .setPositiveButton("确定", (dialog, which) -> {
                        try {
                            if (selected.equals(selectedIds(next))) return;
                            Set<String> ids = ids(apps);
                            next.editSelection(desktop, ids, false);
                            ids.retainAll(selected);
                            next.editSelection(desktop, ids, true);
                            changed.accept(next);
                        } catch (Exception error) { showError(error); }
                    }).show();
        } catch (Exception error) { showError(error); }
    }

    private FrameLayout appTile(JSONObject app, boolean checked, boolean enabled, Consumer<Boolean> change) {
        return tile(app.optString("name"), app.optString("package"), null, "", checked, enabled, change, null);
    }

    private FrameLayout tile(String title, String pkg, List<JSONObject> folderApps, String detail,
            boolean checked, boolean enabled, Consumer<Boolean> change, Runnable open) {
        FrameLayout tile = new FrameLayout(activity);
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER);
        body.setPadding(activity.dp(3), activity.dp(6), activity.dp(3), activity.dp(3));
        if (folderApps == null) body.addView(icon(pkg), new LinearLayout.LayoutParams(activity.dp(36), activity.dp(36)));
        else {
            FrameLayout mosaic = new FrameLayout(activity);
            mosaic.setBackground(activity.roundRect(activity.surfaceStrongColor(), 8));
            for (int i = 0; i < Math.min(4, folderApps.size()); i++) {
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(activity.dp(14), activity.dp(14));
                lp.leftMargin = activity.dp(3 + i % 2 * 16);
                lp.topMargin = activity.dp(3 + i / 2 * 16);
                mosaic.addView(icon(folderApps.get(i).optString("package")), lp);
            }
            body.addView(mosaic, new LinearLayout.LayoutParams(activity.dp(36), activity.dp(36)));
        }
        TextView name = text(title, 12);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        body.addView(name, PageViewUtils.matchWrap());
        if (!detail.isEmpty()) {
            TextView info = text(detail, 11);
            info.setGravity(Gravity.CENTER);
            body.addView(info, PageViewUtils.matchWrap());
        }
        tile.addView(body, new FrameLayout.LayoutParams(-1, -1));
        CheckBox check = new CheckBox(activity);
        check.setTag("selection");
        check.setButtonTintList(ColorStateList.valueOf(activity.primaryColor()));
        check.setContentDescription(title + (folderApps == null ? "，参与整理" : "，整组选中，已选 " + detail));
        check.setChecked(checked);
        check.setEnabled(enabled);
        check.setPadding(0, 0, 0, 0);
        tile.addView(check, new FrameLayout.LayoutParams(activity.dp(32), activity.dp(32), Gravity.TOP | Gravity.RIGHT));
        Consumer<Boolean> paint = value -> tile.setBackground(activity.outlinedRect(
                value ? activity.primaryContainerColor() : activity.surfaceColor(),
                value ? activity.primaryColor() : activity.strokeColor(), 1, 10));
        paint.accept(checked);
        check.setOnCheckedChangeListener((button, value) -> { paint.accept(value); change.accept(value); });
        tile.setAlpha(enabled ? 1f : 0.45f);
        tile.setEnabled(enabled);
        tile.setContentDescription(title + (open == null ? "，点击勾选" : "，点击展开文件夹，已选 " + detail));
        tile.setOnClickListener(view -> { if (open == null) check.performClick(); else open.run(); });
        return tile;
    }

    private ImageView icon(String pkg) {
        ImageView image = new ImageView(activity);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        Drawable.ConstantState cached = icons.get(pkg);
        if (!icons.containsKey(pkg)) {
            Drawable drawable;
            try { drawable = activity.getPackageManager().getApplicationIcon(pkg); }
            catch (Exception ignored) { drawable = activity.getPackageManager().getDefaultActivityIcon(); }
            cached = drawable.getConstantState();
            icons.put(pkg, cached);
        }
        image.setImageDrawable(cached == null ? activity.getPackageManager().getDefaultActivityIcon() : cached.newDrawable(activity.getResources()));
        return image;
    }

    private List<JSONObject> apps(int screen, boolean newApps) throws Exception {
        List<JSONObject> result = new ArrayList<>();
        JSONArray apps = desktop.getJSONArray("apps");
        for (int i = 0; i < apps.length(); i++) {
            JSONObject app = apps.getJSONObject(i);
            if (app.getBoolean("newApp") == newApps && (newApps || app.getInt("screen") == screen)) result.add(app);
        }
        return result;
    }

    private List<JSONObject> folderApps(int folder) throws Exception {
        List<JSONObject> result = new ArrayList<>();
        for (JSONObject app : apps(currentScreen, false)) if (app.getInt("folderId") == folder) result.add(app);
        result.sort(Comparator.comparingInt(app -> app.optInt("rank", 0)));
        return result;
    }

    private Set<String> selectedIds(LauncherOrganizerScope scope) throws Exception {
        Set<String> result = new LinkedHashSet<>();
        JSONArray apps = scope.selectedApps(desktop);
        for (int i = 0; i < apps.length(); i++) result.add(apps.getJSONObject(i).getString("id"));
        return result;
    }

    private static Set<String> ids(List<JSONObject> apps) {
        Set<String> ids = new LinkedHashSet<>();
        for (JSONObject app : apps) ids.add(app.optString("id"));
        return ids;
    }

    private static int selectedCount(List<JSONObject> apps, Set<String> selected) {
        int count = 0;
        for (JSONObject app : apps) if (selected.contains(app.optString("id"))) count++;
        return count;
    }

    private void select(List<JSONObject> apps, boolean selected) {
        try {
            LauncherOrganizerScope next = copy();
            next.editSelection(desktop, ids(apps), selected);
            changed.accept(next);
        } catch (Exception error) { showError(error); }
    }

    private LauncherOrganizerScope copy() {
        try { return new LauncherOrganizerScope(scope.toJson()); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }

    void setBusy(boolean busy) {
        root.setEnabled(!busy);
        if (busy) setEnabled(root, false);
        else render(desktop, scope);
    }

    private static void setEnabled(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) setEnabled(group.getChildAt(i), enabled);
    }

    private void toggle(LinearLayout parent, String title, boolean checked, Consumer<Boolean> changed) {
        CheckBox box = new CheckBox(activity);
        box.setText(title);
        box.setTextColor(activity.textColor());
        box.setButtonTintList(ColorStateList.valueOf(activity.primaryColor()));
        box.setChecked(checked);
        box.setOnCheckedChangeListener((button, value) -> changed.accept(value));
        parent.addView(box, PageViewUtils.matchWrap());
    }

    private LinearLayout row(LinearLayout parent) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(row, activity.matchWrapWithTop(8));
        return row;
    }

    private TextView button(LinearLayout parent, String title, Runnable action) {
        TextView button = activity.filledButton(title, activity.surfaceSoftColor(), activity.primaryColor());
        button.setMinHeight(activity.dp(48));
        activity.setTapClickListener(button, view -> action.run());
        parent.addView(button, parent.getOrientation() == LinearLayout.HORIZONTAL
                ? new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1) : activity.matchWrapWithTop(8));
        return button;
    }

    private TextView text(String text, int size) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(activity.textColor());
        return view;
    }

    private void label(LinearLayout parent, String text) {
        TextView view = text(text, 14);
        view.setTextColor(activity.subtextColor());
        parent.addView(view, activity.matchWrapWithTop(8));
    }

    private void showError(Exception error) {
        new AlertDialog.Builder(activity).setMessage(error.getMessage()).setPositiveButton("确定", null).show();
    }
}
