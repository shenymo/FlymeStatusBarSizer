package com.example.flymestatusbarsizer.feature.launcher.organizer;

import com.example.flymestatusbarsizer.MainActivity;
import com.example.flymestatusbarsizer.R;
import com.example.flymestatusbarsizer.ui.PageViewUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
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
            label(root, "请先在上方步骤 1 中读取桌面数据以展示布局。");
            return;
        }
        try {
            label(root, summary(desktop, scope));
            LinearLayout choiceRow = row(root);
            button(choiceRow, "快捷选择 ▾", this::choosePreset);
            button(choiceRow, "搜索应用", this::searchAllApps);
            TextView presetHint = text("当前范围预设：" + LauncherOrganizerScope.MODES[scope.mode], 12);
            presetHint.setTextColor(activity.subtextColor());
            root.addView(presetHint, activity.matchWrapWithTop(4));
            toggle(root, "保留首屏", "锁定首屏图标和空白位置，保持原位不参与整理", scope.keepFirstScreen, value -> {
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
        int newAppsCount = selected.length() - selectedExisting;
        int keptScreens = scope.protectedScreens(desktop).size();
        int unselectedExisting = existing - selectedExisting;
        StringBuilder sb = new StringBuilder();
        sb.append("已选 ").append(selected.length()).append(" 个应用参与整理");
        if (newAppsCount > 0) sb.append("（含未放桌面 ").append(newAppsCount).append(" 个）");
        sb.append("\n未选 ").append(unselectedExisting).append(" 个桌面应用保持原位");
        if (keptScreens > 0) sb.append("，整页锁定 ").append(keptScreens).append(" 页");
        sb.append("。");
        return sb.toString();
    }

    private void choosePreset() {
        new MaterialAlertDialogBuilder(activity).setTitle("快捷选择（保留锁定页面）")
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
        navigation.setGravity(Gravity.CENTER_VERTICAL);

        ImageView prevBtn = new ImageView(activity);
        prevBtn.setImageResource(R.drawable.ic_chevron_left);
        prevBtn.setImageTintList(ColorStateList.valueOf(activity.textColor()));
        prevBtn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        prevBtn.setBackground(activity.outlinedRect(activity.surfaceStrongColor(), activity.strokeColor(), 1, 10));
        prevBtn.setContentDescription("上一页");
        boolean canPrev = page > 0;
        prevBtn.setEnabled(canPrev);
        prevBtn.setAlpha(canPrev ? 1f : 0.35f);
        if (canPrev) {
            activity.setTapClickListener(prevBtn, v -> switchPage(index - 1));
        }
        navigation.addView(prevBtn, new LinearLayout.LayoutParams(activity.dp(44), activity.dp(42)));

        TextView pageBtn = activity.filledButton("第 " + (page + 1) + " / " + screens.length() + " 页 ▾",
                activity.surfaceStrongColor(), activity.textColor());
        if (pageBtn instanceof MaterialButton mb) {
            mb.setStrokeColor(ColorStateList.valueOf(activity.strokeColor()));
            mb.setStrokeWidth(activity.dp(1));
            mb.setCornerRadius(activity.dp(10));
        }
        pageBtn.setMinHeight(activity.dp(42));
        pageBtn.setTextSize(14);
        activity.setTapClickListener(pageBtn, v -> {
            String[] titles = new String[screens.length()];
            for (int i = 0; i < titles.length; i++) titles[i] = "第 " + (i + 1) + " 页";
            new MaterialAlertDialogBuilder(activity).setTitle("选择桌面页面")
                    .setSingleChoiceItems(titles, index, (dialog, which) -> { dialog.dismiss(); switchPage(which); })
                    .setNegativeButton("取消", null).show();
        });
        LinearLayout.LayoutParams centerLp = new LinearLayout.LayoutParams(0, activity.dp(42), 1f);
        centerLp.leftMargin = activity.dp(8);
        centerLp.rightMargin = activity.dp(8);
        navigation.addView(pageBtn, centerLp);

        ImageView nextBtn = new ImageView(activity);
        nextBtn.setImageResource(R.drawable.ic_chevron_right);
        nextBtn.setImageTintList(ColorStateList.valueOf(activity.textColor()));
        nextBtn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        nextBtn.setBackground(activity.outlinedRect(activity.surfaceStrongColor(), activity.strokeColor(), 1, 10));
        nextBtn.setContentDescription("下一页");
        boolean canNext = page < screens.length() - 1;
        nextBtn.setEnabled(canNext);
        nextBtn.setAlpha(canNext ? 1f : 0.35f);
        if (canNext) {
            activity.setTapClickListener(nextBtn, v -> switchPage(index + 1));
        }
        navigation.addView(nextBtn, new LinearLayout.LayoutParams(activity.dp(44), activity.dp(42)));

        boolean locked = scope.protectedScreens(desktop).contains(currentScreen);
        if (page != 0) toggle(root, "保留本页", "锁定第 " + (page + 1) + " 页图标和空白位置，保持原位不参与整理", locked, value -> {
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
            LinearLayout dialogView = new LinearLayout(activity);
            dialogView.setOrientation(LinearLayout.VERTICAL);
            dialogView.setPadding(activity.dp(20), activity.dp(8), activity.dp(20), 0);

            // Pinned Header
            LinearLayout fixedHeader = new LinearLayout(activity);
            fixedHeader.setOrientation(LinearLayout.VERTICAL);

            EditText search = new EditText(activity);
            search.setHint("搜索应用名称...");
            search.setSingleLine(true);
            search.setTextSize(14);
            search.setTextColor(activity.textColor());
            search.setHintTextColor(activity.subtextColor());
            search.setBackground(activity.roundRect(activity.surfaceStrongColor(), 8));
            search.setPadding(activity.dp(12), activity.dp(8), activity.dp(12), activity.dp(8));
            fixedHeader.addView(search, PageViewUtils.matchWrap());

            TextView count = text("", 13);
            count.setTextColor(activity.subtextColor());
            fixedHeader.addView(count, activity.matchWrapWithTop(6));

            LinearLayout actions = new LinearLayout(activity);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            fixedHeader.addView(actions, activity.matchWrapWithTop(6));

            dialogView.addView(fixedHeader, PageViewUtils.matchWrap());

            // Scrollable Content
            FrameLayout gridContainer = new FrameLayout(activity);
            ScrollView scroll = new ScrollView(activity) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    int maxHeight = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.48f);
                    heightMeasureSpec = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST);
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                }
            };
            scroll.addView(gridContainer, PageViewUtils.matchWrap());
            dialogView.addView(scroll, activity.matchWrapWithTop(8));

            List<JSONObject> currentFiltered = new ArrayList<>();

            Runnable updateCount = () -> {
                String query = search.getText().toString().trim();
                if (query.isEmpty()) {
                    count.setText("已选 " + selectedCount(apps, selected) + "/" + apps.size() + " 个应用");
                } else {
                    count.setText("匹配 " + currentFiltered.size() + " 个（已选 " + selectedCount(currentFiltered, selected) + " 个）");
                }
            };

            Runnable renderGrid = () -> {
                gridContainer.removeAllViews();
                currentFiltered.clear();
                String query = search.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
                for (JSONObject app : apps) {
                    String name = app.optString("name", "");
                    String pkg = app.optString("package", "");
                    if (query.isEmpty() || name.toLowerCase(java.util.Locale.ROOT).contains(query)
                            || pkg.toLowerCase(java.util.Locale.ROOT).contains(query)) {
                        currentFiltered.add(app);
                    }
                }
                if (currentFiltered.isEmpty()) {
                    TextView empty = text("未找到匹配的应用", 13);
                    empty.setTextColor(activity.subtextColor());
                    empty.setGravity(Gravity.CENTER);
                    gridContainer.addView(empty, PageViewUtils.matchWrapWithTop(activity, 16));
                } else {
                    int columns = 3;
                    int rows = (currentFiltered.size() + columns - 1) / columns;
                    LauncherOrganizerDesktopGrid grid = new LauncherOrganizerDesktopGrid(activity, columns, rows);
                    for (int i = 0; i < currentFiltered.size(); i++) {
                        JSONObject app = currentFiltered.get(i);
                        String id = app.optString("id");
                        FrameLayout tile = appTile(app, selected.contains(id), true, value -> {
                            if (value) selected.add(id); else selected.remove(id);
                            updateCount.run();
                        });
                        grid.addItem(tile, i % columns, i / columns, 1, 1);
                    }
                    gridContainer.addView(grid, PageViewUtils.matchWrap());
                }
                updateCount.run();
            };

            button(actions, "全选", () -> {
                for (JSONObject app : currentFiltered) selected.add(app.optString("id"));
                renderGrid.run();
            });
            button(actions, "清空", () -> {
                for (JSONObject app : currentFiltered) selected.remove(app.optString("id"));
                renderGrid.run();
            });

            search.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) { renderGrid.run(); }
            });

            renderGrid.run();

            new MaterialAlertDialogBuilder(activity).setTitle(title).setView(dialogView)
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

    private void searchAllApps() {
        try {
            if (desktop == null) return;
            LauncherOrganizerScope next = copy();
            Set<String> selected = selectedIds(next);
            JSONArray allAppsJson = desktop.getJSONArray("apps");
            List<JSONObject> allApps = new ArrayList<>();
            for (int i = 0; i < allAppsJson.length(); i++) {
                allApps.add(allAppsJson.getJSONObject(i));
            }
            allApps.sort(Comparator.comparing(app -> app.optString("name", "")));

            LinearLayout dialogView = new LinearLayout(activity);
            dialogView.setOrientation(LinearLayout.VERTICAL);
            dialogView.setPadding(activity.dp(20), activity.dp(8), activity.dp(20), 0);

            // Fixed Header: search, count, actions pinned at top
            LinearLayout fixedHeader = new LinearLayout(activity);
            fixedHeader.setOrientation(LinearLayout.VERTICAL);

            EditText search = new EditText(activity);
            search.setHint("搜索应用名称或包名...");
            search.setSingleLine(true);
            search.setTextSize(14);
            search.setTextColor(activity.textColor());
            search.setHintTextColor(activity.subtextColor());
            search.setBackground(activity.roundRect(activity.surfaceStrongColor(), 8));
            search.setPadding(activity.dp(12), activity.dp(8), activity.dp(12), activity.dp(8));
            fixedHeader.addView(search, PageViewUtils.matchWrap());

            TextView count = text("", 13);
            count.setTextColor(activity.subtextColor());
            fixedHeader.addView(count, activity.matchWrapWithTop(6));

            LinearLayout actions = new LinearLayout(activity);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            fixedHeader.addView(actions, activity.matchWrapWithTop(6));

            dialogView.addView(fixedHeader, PageViewUtils.matchWrap());

            // Scrollable Content: ONLY the list scrolls
            LinearLayout listContainer = new LinearLayout(activity);
            listContainer.setOrientation(LinearLayout.VERTICAL);

            ScrollView scroll = new ScrollView(activity) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    int maxHeight = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.48f);
                    heightMeasureSpec = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST);
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                }
            };
            scroll.addView(listContainer, PageViewUtils.matchWrap());
            dialogView.addView(scroll, activity.matchWrapWithTop(8));

            List<JSONObject> currentFiltered = new ArrayList<>();

            Runnable updateCount = () -> {
                count.setText("匹配 " + currentFiltered.size() + " 个（已选 " + selectedCount(currentFiltered, selected) + " 个）");
            };

            Runnable renderList = () -> {
                listContainer.removeAllViews();
                currentFiltered.clear();
                String query = search.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
                for (JSONObject app : allApps) {
                    String name = app.optString("name", "");
                    String pkg = app.optString("package", "");
                    if (query.isEmpty() || name.toLowerCase(java.util.Locale.ROOT).contains(query)
                            || pkg.toLowerCase(java.util.Locale.ROOT).contains(query)) {
                        currentFiltered.add(app);
                    }
                }
                if (currentFiltered.isEmpty()) {
                    TextView empty = text("未找到匹配的应用", 13);
                    empty.setTextColor(activity.subtextColor());
                    empty.setGravity(Gravity.CENTER);
                    listContainer.addView(empty, PageViewUtils.matchWrapWithTop(activity, 16));
                } else {
                    for (JSONObject app : currentFiltered) {
                        String id = app.optString("id");
                        String name = app.optString("name");
                        String pkg = app.optString("package");
                        boolean newApp = app.optBoolean("newApp", false);
                        int screen = app.optInt("screen", -1);
                        int folderId = app.optInt("folderId", -1);

                        String location;
                        if (newApp) location = "未放到桌面";
                        else if (folderId >= 0) location = "第 " + (screen + 1) + " 页 · 文件夹内";
                        else location = "第 " + (screen + 1) + " 页";

                        LinearLayout row = new LinearLayout(activity);
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        row.setGravity(Gravity.CENTER_VERTICAL);
                        row.setPadding(activity.dp(4), activity.dp(6), activity.dp(4), activity.dp(6));

                        ImageView iconView = icon(pkg);
                        row.addView(iconView, new LinearLayout.LayoutParams(activity.dp(32), activity.dp(32)));

                        LinearLayout textCol = new LinearLayout(activity);
                        textCol.setOrientation(LinearLayout.VERTICAL);
                        textCol.setPadding(activity.dp(8), 0, activity.dp(8), 0);

                        TextView titleView = text(name, 14);
                        textCol.addView(titleView);

                        TextView locView = text(location, 11);
                        locView.setTextColor(activity.subtextColor());
                        textCol.addView(locView);

                        row.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                        CheckBox check = new CheckBox(activity);
                        check.setButtonTintList(ColorStateList.valueOf(activity.primaryColor()));
                        check.setChecked(selected.contains(id));
                        check.setOnCheckedChangeListener((btn, isChecked) -> {
                            if (isChecked) selected.add(id); else selected.remove(id);
                            updateCount.run();
                        });
                        row.addView(check);

                        row.setOnClickListener(v -> check.performClick());
                        listContainer.addView(row);
                    }
                }
                updateCount.run();
            };

            button(actions, "全选", () -> {
                for (JSONObject app : currentFiltered) selected.add(app.optString("id"));
                renderList.run();
            });
            button(actions, "清空", () -> {
                for (JSONObject app : currentFiltered) selected.remove(app.optString("id"));
                renderList.run();
            });

            search.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) { renderList.run(); }
            });

            renderList.run();

            new MaterialAlertDialogBuilder(activity).setTitle("按名称搜索应用").setView(dialogView)
                    .setNegativeButton("取消", null)
                    .setPositiveButton("确定", (dialog, which) -> {
                        try {
                            if (selected.equals(selectedIds(next))) return;
                            Set<String> allIds = ids(allApps);
                            next.editSelection(desktop, allIds, false);
                            allIds.retainAll(selected);
                            next.editSelection(desktop, allIds, true);
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

    private void toggle(LinearLayout parent, String title, String subtitle, boolean checked, Consumer<Boolean> changed) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(activity.dp(14), activity.dp(8), activity.dp(14), activity.dp(8));
        row.setBackground(activity.outlinedRect(activity.surfaceStrongColor(), activity.strokeColor(), 1, 10));

        LinearLayout textCol = new LinearLayout(activity);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = text(title, 14);
        titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        textCol.addView(titleView);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView subView = text(subtitle, 12);
            subView.setTextColor(activity.subtextColor());
            textCol.addView(subView, activity.matchWrapWithTop(2));
        }

        row.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        MaterialSwitch toggleSwitch = new MaterialSwitch(activity);
        activity.styleSwitch(toggleSwitch);
        toggleSwitch.setChecked(checked);
        toggleSwitch.setOnCheckedChangeListener((btn, isChecked) -> changed.accept(isChecked));
        row.addView(toggleSwitch, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        activity.setTapClickListener(row, v -> toggleSwitch.toggle());

        parent.addView(row, activity.matchWrapWithTop(8));
    }

    private LinearLayout row(LinearLayout parent) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);
        parent.addView(row, activity.matchWrapWithTop(8));
        return row;
    }

    private TextView button(LinearLayout parent, String title, Runnable action) {
        TextView button = activity.filledButton(title, activity.surfaceStrongColor(), activity.textColor());
        if (button instanceof MaterialButton mb) {
            mb.setStrokeColor(ColorStateList.valueOf(activity.strokeColor()));
            mb.setStrokeWidth(activity.dp(1));
            mb.setCornerRadius(activity.dp(10));
            mb.setInsetTop(0);
            mb.setInsetBottom(0);
        }
        button.setMinHeight(activity.dp(40));
        button.setMaxLines(1);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setGravity(Gravity.CENTER);
        button.setPadding(activity.dp(8), 0, activity.dp(8), 0);
        activity.setTapClickListener(button, view -> action.run());
        if (parent.getOrientation() == LinearLayout.HORIZONTAL) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            if (parent.getChildCount() > 0) {
                lp.leftMargin = activity.dp(8);
            }
            parent.addView(button, lp);
        } else {
            parent.addView(button, activity.matchWrapWithTop(8));
        }
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
        new MaterialAlertDialogBuilder(activity).setMessage(error.getMessage()).setPositiveButton("确定", null).show();
    }
}
