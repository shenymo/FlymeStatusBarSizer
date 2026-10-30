package com.example.flymestatusbarsizer.feature.launcher.organizer;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;
import com.example.flymestatusbarsizer.config.ModuleConfig;
import com.example.flymestatusbarsizer.util.ReflectUtils;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LauncherOrganizer {
    private static final String BASE = "com.android.launcher3.";
    private static final String MZ = "com.meizu.flyme.launcher.";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private static WeakReference<Activity> launcher = new WeakReference<>(null);
    private static SharedPreferences observedPrefs;
    private static final SharedPreferences.OnSharedPreferenceChangeListener LISTENER = (prefs, key) -> {
        if (LauncherOrganizerProvider.SIGNAL.equals(key)) MAIN.post(LauncherOrganizer::dispatch);
    };

    public static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> type = Class.forName(BASE + "Launcher", false, loader);
            for (Method method : type.getDeclaredMethods()) {
                if ((method.getName().equals("onCreate") && method.getParameterCount() == 1)
                        || (method.getName().equals("onResume") && method.getParameterCount() == 0)) {
                    module.intercept(method, chain -> {
                        Object result = chain.proceed();
                        if (chain.getThisObject() instanceof Activity activity) {
                            MAIN.post(() -> {
                                launcher = new WeakReference<>(activity);
                                SharedPreferences prefs = ModuleConfig.getRemotePreferences();
                                if (prefs != null && prefs != observedPrefs) {
                                    if (observedPrefs != null) observedPrefs.unregisterOnSharedPreferenceChangeListener(LISTENER);
                                    observedPrefs = prefs;
                                    prefs.registerOnSharedPreferenceChangeListener(LISTENER);
                                }
                                dispatch();
                            });
                        }
                        return result;
                    });
                }
            }
        } catch (Throwable error) {
            FlymeStatusBarSizer.logLauncherWarning("Failed to install desktop organizer", error);
        }
    }

    private static void dispatch() {
        Activity activity = launcher.get();
        if (activity == null || activity.isDestroyed() || !BUSY.compareAndSet(false, true)) return;
        try {
            // Capture view state on the UI thread; all database work runs on the model executor.
            boolean ready = !(boolean) call(activity, "isWorkspaceLoading")
                    && !(boolean) call(activity, "isInEditMode")
                    && !(boolean) call(call(activity, "getDragController"), "isDragging");
            Object profile = call(activity, "getDeviceProfile");
            int[] screenOrder = screenOrder(activity);
            Executor executor = (Executor) Class.forName(BASE + "util.Executors", false, activity.getClassLoader())
                    .getField("MODEL_EXECUTOR").get(null);
            executor.execute(() -> execute(activity, profile, ready, screenOrder));
        } catch (Throwable error) {
            BUSY.set(false);
            FlymeStatusBarSizer.logLauncherWarning("Desktop organizer dispatch failed", error);
        }
    }

    private static void execute(Activity activity, Object profile, boolean ready, int[] screenOrder) {
        String id = null;
        Bundle reply = new Bundle();
        boolean changed = false;
        boolean locked = false;
        boolean reloaded = false;
        try {
            Bundle mailbox = activity.getContentResolver().call(LauncherOrganizerProvider.URI, "take", null, null);
            String text = mailbox == null ? "" : mailbox.getString("request", "");
            if (text.isEmpty()) return;
            JSONObject request = new JSONObject(text);
            id = request.getString("id");
            if (!ready) throw new IllegalStateException("请等待桌面加载完成，并退出桌面编辑或拖拽状态后重试");
            Object model = call(activity, "getModel");
            if (!(boolean) call(model, "isModelLoaded")) throw new IllegalStateException("桌面尚未加载完成");
            ClassLoader loader = activity.getClassLoader();
            Object privacy = call(Class.forName(MZ + "privacymode.PrivacyModeManager", false, loader), "getInstance");
            if ((boolean) call(privacy, "isPrivacyModeRunning")
                    || (boolean) call(Class.forName(MZ + "utils.FlymeEasyModeUtils", false, loader), "isInEasyMode", Context.class, activity)) {
                throw new IllegalStateException("请在普通桌面模式下整理");
            }
            Object controller = call(model, "getModelDbController");
            SQLiteDatabase db = (SQLiteDatabase) call(call(controller, "getDbHelper"), "getWritableDatabase");
            JSONObject state = snapshot(activity, profile, model, privacy, db, screenOrder);
            String action = request.getString("action");
            if ("read".equals(action)) {
                reply.putString("data", LauncherOrganizerDesktop.exported(state).toString());
            } else {
                if ((boolean) call(Class.forName(MZ + "utils.WorkspaceLayoutLockUtils", false, loader),
                        "isLauncherLayoutLocked", Context.class, activity)) {
                    throw new IllegalStateException("请先关闭桌面的“锁定布局”");
                }
                FutureTask<Void> lock = new FutureTask<>(() -> {
                    if (activity.isDestroyed() || (boolean) call(activity, "isWorkspaceLoading")
                            || (boolean) call(activity, "isInEditMode")
                            || (boolean) call(call(activity, "getDragController"), "isDragging")) {
                        throw new IllegalStateException("桌面正忙，请稍后重试");
                    }
                    if (!java.util.Arrays.equals(screenOrder, screenOrder(activity))) {
                        throw new IllegalStateException("桌面页面顺序已变化，请重新读取");
                    }
                    call(activity, "setWorkspaceLoadingMz", boolean.class, true);
                    return null;
                });
                MAIN.post(lock);
                try { lock.get(5, TimeUnit.SECONDS); }
                finally { lock.cancel(false); }
                locked = true;
                if ("apply".equals(action)) {
                    JSONObject plan = new JSONObject(request.getString("data"));
                    if (!state.getString("hash").equals(plan.getString("hash"))) {
                        throw new IllegalStateException("桌面或应用列表已变化，请重新读取并生成分类");
                    }
                    LauncherOrganizerScope scope = new LauncherOrganizerScope(plan.getJSONObject("scope"));
                    apply(activity, profile, controller, db, state, plan.getJSONArray("groups"), scope);
                } else if ("sort_usage".equals(action)) {
                    JSONObject plan = new JSONObject(request.getString("data"));
                    if (!state.getString("hash").equals(plan.getString("hash"))) {
                        throw new IllegalStateException("桌面或应用列表已变化，请重新读取后重试");
                    }
                    sortExistingFolders(profile, activity.getClassLoader(), db, state, plan.getJSONObject("usage"));
                } else {
                    throw new IllegalArgumentException("未知桌面操作");
                }
                changed = true;
                // Invalidate old model state before allowing subsequent model tasks to execute.
                call(model, "forceReload");
                reloaded = true;
                reply.putString("data", "整理已保存，桌面正在刷新");
            }
        } catch (Throwable error) {
            while (error.getCause() != null) error = error.getCause();
            reply.putString("error", (changed ? "布局已保存，但刷新失败；请重启桌面。" : "")
                    + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
            FlymeStatusBarSizer.logLauncherWarning("Desktop organizer operation failed", error);
        } finally {
            if (locked && !reloaded) MAIN.post(() -> {
                try { call(activity, "setWorkspaceLoadingMz", boolean.class, false); }
                catch (Exception error) { FlymeStatusBarSizer.logLauncherWarning("Desktop organizer unlock failed", error); }
            });
            BUSY.set(false);
            if (id != null) {
                try { activity.getContentResolver().call(LauncherOrganizerProvider.URI, "finish", id, reply); }
                catch (Throwable error) { FlymeStatusBarSizer.logLauncherWarning("Desktop organizer reply failed", error); }
            }
        }
    }

    private static int[] screenOrder(Activity activity) throws Exception {
        return (int[]) call(call(call(activity, "getWorkspace"), "getScreenOrder"), "toArray");
    }

    private static JSONObject snapshot(Activity activity, Object profile, Object model, Object privacy,
            SQLiteDatabase db, int[] screenOrder) throws Exception {
        Object inv = requiredField(profile, "inv");
        int columns = (int) requiredField(inv, "numColumns");
        int rows = (int) requiredField(inv, "numRows");
        JSONArray items = readRows(db);
        Map<Integer, JSONObject> byId = new LinkedHashMap<>();
        Set<Integer> folders = new HashSet<>();
        Set<String> existing = new HashSet<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject row = items.getJSONObject(i);
            byId.put(row.getInt("_id"), row);
            if (row.optInt("itemType", -1) == 0) existing.add(identity(row));
            if (row.optInt("itemType", -1) == 2 && onDesktop(row)
                    && row.optInt("category", -1) != 13 && row.optInt("category", -1) != 14) {
                folders.add(row.getInt("_id"));
            }
        }
        Object appList = requiredField(model, "mBgAllAppsList");
        List<?> allApps = (List<?>) requiredField(appList, "data");
        TreeMap<String, JSONObject> available = new TreeMap<>();
        UserManager users = activity.getSystemService(UserManager.class);
        for (Object app : new ArrayList<>(allApps)) {
            if ((boolean) call(app, "isDisabled")
                    || (boolean) call(privacy, "isPrivacyItem", Class.forName(BASE + "model.data.ItemInfo", false, activity.getClassLoader()), app)) continue;
            Object info = call(app, "makeWorkspaceItem", Context.class, activity);
            ContentValues values = new ContentValues();
            call(info, "onAddToDatabase", ContentValues.class, values);
            values.put("profileId", users.getSerialNumberForUser((UserHandle) requiredField(info, "user")));
            if (values.getAsLong("profileId") < 0 || values.getAsInteger("restored") != 0) continue;
            values.put("options", (int) requiredField(info, "options"));
            JSONObject row = jsonValues(values);
            if (!packageName(row).isEmpty()) available.put(identity(row), row);
        }
        TreeMap<String, JSONObject> apps = new TreeMap<>();
        for (JSONObject row : byId.values()) {
            if (row.optInt("itemType", -1) != 0 || row.optInt("restored", 0) != 0) continue;
            if (!onDesktop(row) && !folders.contains(row.optInt("container", -1))) continue;
            if (!available.containsKey(identity(row))) continue;
            String id = Integer.toString(row.getInt("_id"));
            apps.put(id, appJson(id, row));
        }
        for (Map.Entry<String, JSONObject> entry : available.entrySet()) {
            if (!existing.contains(entry.getKey())) {
                String id = "new:" + entry.getKey();
                apps.put(id, appJson(id, entry.getValue()));
            }
        }
        if (apps.isEmpty()) throw new IllegalStateException("没有可整理的应用");
        JSONArray appArray = new JSONArray();
        for (JSONObject app : apps.values()) {
            JSONObject row = app.getJSONObject("row");
            boolean isNew = !row.has("_id");
            int folder = !isNew && !onDesktop(row) ? row.getInt("container") : -1;
            JSONObject parent = folder < 0 ? row : byId.get(folder);
            app.put("newApp", isNew).put("folderId", folder)
                    .put("screen", isNew ? -1 : parent.getInt("screen"));
            appArray.put(app);
        }
        LinkedHashSet<Integer> screens = new LinkedHashSet<>();
        for (int screen : screenOrder) if (screen >= 0 && screen < 100_000_000) screens.add(screen);
        for (JSONObject row : byId.values()) if (onDesktop(row)) screens.add(row.getInt("screen"));
        if (screens.isEmpty()) screens.add(0);
        JSONArray folderInfo = new JSONArray();
        for (int folder : new TreeSet<>(folders)) {
            JSONObject row = byId.get(folder);
            folderInfo.put(new JSONObject().put("id", folder).put("name", row.optString("title", "文件夹"))
                    .put("screen", row.getInt("screen")));
        }
        JSONObject state = new JSONObject().put("database", db.getPath()).put("columns", columns)
                .put("rows", rows).put("items", items).put("apps", appArray)
                .put("folders", folderInfo).put("screens", new JSONArray(screens))
                .put("scopeVersion", LauncherOrganizerScope.VERSION);
        state.put("layoutHash", layoutHash(state, items));
        JSONArray identities = new JSONArray();
        for (JSONObject app : apps.values()) identities.put(new JSONArray().put(app.getString("id")).put(app.getString("name")));
        state.put("hash", digest(state.getString("layoutHash") + identities));
        return state;
    }

    private static JSONObject appJson(String id, JSONObject row) throws Exception {
        String name = row.optString("title", packageName(row));
        if (row.optInt("cloneId", 0) != 0) name += "（分身 " + row.getInt("cloneId") + "）";
        return new JSONObject().put("id", id).put("name", name).put("package", packageName(row)).put("row", row);
    }

    private static void apply(Context context, Object profile, Object controller, SQLiteDatabase db,
            JSONObject state, JSONArray groups, LauncherOrganizerScope scope) throws Exception {
        JSONArray apps = scope.selectedApps(state);
        List<String> unclassified = LauncherOrganizerProvider.validateGroups(apps, groups);
        // Reuse single-app placement so each unclassified app gets its own desktop cell.
        for (String id : unclassified) {
            groups.put(new JSONObject().put("apps", new JSONArray().put(id)));
        }
        Map<String, JSONObject> appRows = new LinkedHashMap<>();
        Set<Integer> moving = new HashSet<>();
        for (int i = 0; i < apps.length(); i++) {
            JSONObject app = apps.getJSONObject(i);
            JSONObject row = app.getJSONObject("row");
            appRows.put(app.getString("id"), row);
            if (row.has("_id")) moving.add(row.getInt("_id"));
        }
        JSONArray items = state.getJSONArray("items");
        Set<Integer> removedFolders = LauncherOrganizerScope.removableFolders(items, moving);
        Set<Integer> targetFolders = new HashSet<>();
        for (int i = 0; i < groups.length(); i++) {
            int targetFolder = groups.getJSONObject(i).optInt("folderId", -1);
            if (targetFolder >= 0) targetFolders.add(targetFolder);
        }
        removedFolders.removeAll(targetFolders);
        List<Integer> screens = new ArrayList<>();
        JSONArray screenIds = state.getJSONArray("screens");
        for (int i = 0; i < screenIds.length(); i++) screens.add(screenIds.getInt(i));
        List<int[]> reserved = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject row = items.getJSONObject(i);
            int id = row.getInt("_id");
            if (!onDesktop(row)) continue;
            if (!moving.contains(id) && !removedFolders.contains(id)) {
                reserved.add(new int[]{row.getInt("screen"), row.getInt("cellX"), row.getInt("cellY"), row.getInt("spanX"), row.getInt("spanY")});
            }
        }
        List<int[]> sizes = new ArrayList<>();
        List<Integer> positionedGroups = new ArrayList<>();
        Map<Integer, int[]> groupSizes = new HashMap<>();
        Set<Integer> validFolders = new HashSet<>();
        JSONArray folderInfo = state.getJSONArray("folders");
        for (int i = 0; i < folderInfo.length(); i++) validFolders.add(folderInfo.getJSONObject(i).getInt("id"));
        for (int i = 0; i < groups.length(); i++) {
            JSONObject group = groups.getJSONObject(i);
            int targetFolder = group.optInt("folderId", -1);
            if (targetFolder >= 0 && !validFolders.contains(targetFolder)) {
                throw new IllegalArgumentException("目标文件夹已不存在，请重新读取桌面");
            }
            if (targetFolder < 0) {
                positionedGroups.add(i);
                int[] size = LauncherOrganizerLayout.folderSpan(LauncherOrganizerLayout.folderType(
                        group.optInt("folderType", -1), group.getJSONArray("apps").length(),
                        state.getInt("columns"), state.getInt("rows")));
                sizes.add(size);
                groupSizes.put(i, size);
            }
        }
        List<int[]> positions = LauncherOrganizerLayout.place(state.getInt("columns"), state.getInt("rows"),
                screens, reserved, sizes, scope.protectedScreens(state));
        Map<Integer, int[]> groupPositions = new HashMap<>();
        for (int i = 0; i < positionedGroups.size(); i++) groupPositions.put(positionedGroups.get(i), positions.get(i));
        JSONArray writes = new JSONArray();
        ClassLoader loader = context.getClassLoader();
        Map<Integer, Integer> appendedCounts = new HashMap<>();
        for (int i = 0; i < groups.length(); i++) {
            JSONObject group = groups.getJSONObject(i);
            JSONArray ids = group.getJSONArray("apps");
            int targetFolder = group.optInt("folderId", -1);
            int[] pos = groupPositions.get(i);
            int container = targetFolder >= 0 ? targetFolder : -100;
            int screen = targetFolder >= 0 ? folder(folderInfo, targetFolder).getInt("screen") : pos[0];
            int startRank = 0;
            Object grid = null;
            if (targetFolder >= 0) {
                startRank = folderNextRank(items, targetFolder)
                        + appendedCounts.getOrDefault(targetFolder, 0);
                Class<?> gridType = Class.forName(BASE + "folder.FolderGridOrganizer", false, loader);
                grid = gridType.getConstructor(int.class, int.class).newInstance(
                        (int) requiredField(profile, "numFolderColumns"), (int) requiredField(profile, "numFolderRows"));
                call(grid, "setContentSize", int.class, startRank + ids.length());
            } else if (ids.length() > 1) {
                Class<?> gridType = Class.forName(BASE + "folder.FolderGridOrganizer", false, loader);
                int folderId = (int) call(controller, "generateNewItemId");
                JSONObject folder = new JSONObject().put("_id", folderId).put("itemType", 2)
                        .put("title", group.getString("name")).put("profileId", context.getSystemService(UserManager.class).getSerialNumberForUser(Process.myUserHandle()))
                        .put("options", 8).put("category", -2).put("cloneId", 0);
                position(folder, -100, pos[0], pos[1], pos[2], 0);
                folder.put("spanX", groupSizes.get(i)[0]).put("spanY", groupSizes.get(i)[1]);
                writes.put(folder);
                container = folderId;
                grid = gridType.getConstructor(int.class, int.class).newInstance(
                        (int) requiredField(profile, "numFolderColumns"), (int) requiredField(profile, "numFolderRows"));
                call(grid, "setContentSize", int.class, ids.length());
            }
            for (int rank = 0; rank < ids.length(); rank++) {
                JSONObject row = new JSONObject(appRows.get(ids.getString(rank)).toString());
                if (!row.has("_id")) {
                    int newId = (int) call(controller, "generateNewItemId");
                    row.put("_id", newId);
                }
                int effectiveRank = startRank + rank;
                Point cell = grid == null ? new Point(pos[1], pos[2]) : (Point) call(grid, "getPosForRank", int.class, effectiveRank);
                position(row, container, screen, cell.x, cell.y, effectiveRank);
                writes.put(row);
            }
            if (targetFolder >= 0) appendedCounts.put(targetFolder,
                    appendedCounts.getOrDefault(targetFolder, 0) + ids.length());
        }
        db.beginTransaction();
        try {
            // Recheck inside the transaction; no partial layout is made visible.
            if (!state.getString("layoutHash").equals(layoutHash(state, readRows(db)))) throw new IllegalStateException("布局已变化，请重新读取");
            for (int i = 0; i < writes.length(); i++) {
                JSONObject row = writes.getJSONObject(i);
                ContentValues values = contentValues(row);
                int id = row.getInt("_id");
                if (moving.contains(id)) {
                    if (db.update("favorites", values, "_id=?", new String[]{Integer.toString(id)}) != 1) throw new IllegalStateException("应用记录已变化");
                } else {
                    db.insertOrThrow("favorites", null, values);
                }
            }
            for (int id : removedFolders) db.delete("favorites", "_id=?", new String[]{Integer.toString(id)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private static void sortExistingFolders(Object profile, ClassLoader loader, SQLiteDatabase db,
            JSONObject state, JSONObject usage) throws Exception {
        JSONArray folderInfo = state.getJSONArray("folders");
        Set<Integer> folders = new HashSet<>();
        for (int i = 0; i < folderInfo.length(); i++) folders.add(folderInfo.getJSONObject(i).getInt("id"));
        JSONArray writes = sortTargetFolders(profile, loader, state.getJSONArray("items"),
                new JSONArray(), folders, usage, folderInfo, new HashSet<>());
        db.beginTransaction();
        try {
            if (!state.getString("layoutHash").equals(layoutHash(state, readRows(db)))) {
                throw new IllegalStateException("布局已变化，请重新读取后重试");
            }
            for (int i = 0; i < writes.length(); i++) {
                JSONObject row = writes.getJSONObject(i);
                ContentValues values = contentValues(row);
                if (db.update("favorites", values, "_id=?", new String[]{Integer.toString(row.getInt("_id"))}) != 1) {
                    throw new IllegalStateException("应用记录已变化，请重新读取后重试");
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private static JSONArray sortTargetFolders(Object profile, ClassLoader loader,
            JSONArray items, JSONArray writes, Set<Integer> targetFolders, JSONObject usage,
            JSONArray folderInfo, Set<Integer> moving) throws Exception {
        Map<Integer, Map<Integer, JSONObject>> childrenByFolder = new HashMap<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject row = items.getJSONObject(i);
            int folder = row.optInt("container", -1);
            if (targetFolders.contains(folder) && row.optInt("itemType", -1) == 0
                    && !moving.contains(row.getInt("_id"))) {
                childrenByFolder.computeIfAbsent(folder, ignored -> new LinkedHashMap<>()).put(row.getInt("_id"), row);
            }
        }
        for (int i = 0; i < writes.length(); i++) {
            JSONObject row = writes.getJSONObject(i);
            int folder = row.optInt("container", -1);
            if (targetFolders.contains(folder) && row.optInt("itemType", -1) == 0) {
                childrenByFolder.computeIfAbsent(folder, ignored -> new LinkedHashMap<>()).put(row.getInt("_id"), row);
            }
        }
        JSONArray result = new JSONArray();
        for (int i = 0; i < writes.length(); i++) {
            JSONObject row = writes.getJSONObject(i);
            if (!targetFolders.contains(row.optInt("container", -1))) result.put(row);
        }
        int columns = (int) requiredField(profile, "numFolderColumns");
        int rows = (int) requiredField(profile, "numFolderRows");
        Class<?> gridType = Class.forName(BASE + "folder.FolderGridOrganizer", false, loader);
        for (int folderId : targetFolders) {
            List<FolderChild> children = new ArrayList<>();
            for (JSONObject row : childrenByFolder.getOrDefault(folderId, new LinkedHashMap<>()).values()) {
                JSONObject score = usage.optJSONObject(packageName(row));
                children.add(new FolderChild(row, score == null ? 0 : score.optLong("count"),
                        score == null ? 0 : score.optLong("last")));
            }
            children.sort((left, right) -> {
                int countOrder = Long.compare(right.count, left.count);
                if (countOrder != 0) return countOrder;
                int recentOrder = Long.compare(right.last, left.last);
                return recentOrder != 0 ? recentOrder
                        : Integer.compare(left.row.optInt("rank", 0), right.row.optInt("rank", 0));
            });
            Object grid = gridType.getConstructor(int.class, int.class).newInstance(columns, rows);
            call(grid, "setContentSize", int.class, children.size());
            int screen = folder(folderInfo, folderId).getInt("screen");
            for (int rank = 0; rank < children.size(); rank++) {
                JSONObject row = children.get(rank).row;
                Point cell = (Point) call(grid, "getPosForRank", int.class, rank);
                position(row, folderId, screen, cell.x, cell.y, rank);
                result.put(row);
            }
        }
        return result;
    }

    private static final class FolderChild {
        final JSONObject row;
        final long count;
        final long last;

        FolderChild(JSONObject row, long count, long last) {
            this.row = row;
            this.count = count;
            this.last = last;
        }
    }

    private static void position(JSONObject row, int container, int screen, int x, int y, int rank) throws Exception {
        row.put("container", container).put("screen", screen).put("cellX", x).put("cellY", y)
                .put("spanX", 1).put("spanY", 1).put("rank", rank);
    }

    private static JSONObject folder(JSONArray folders, int folderId) throws Exception {
        for (int i = 0; i < folders.length(); i++) {
            JSONObject folder = folders.getJSONObject(i);
            if (folder.getInt("id") == folderId) return folder;
        }
        return null;
    }

    private static int folderNextRank(JSONArray items, int folderId) throws Exception {
        int nextRank = 0;
        for (int i = 0; i < items.length(); i++) {
            JSONObject row = items.getJSONObject(i);
            if (row.optInt("container", -1) == folderId && row.optInt("itemType", -1) == 0) {
                nextRank = Math.max(nextRank, row.optInt("rank", -1) + 1);
            }
        }
        return nextRank;
    }

    private static boolean onDesktop(JSONObject row) {
        return row.optInt("container", -1) == -100 && row.optInt("screen", -1) >= 0 && row.optInt("screen", -1) < 100_000_000;
    }

    private static String packageName(JSONObject row) throws Exception {
        Intent intent = Intent.parseUri(row.optString("intent", ""), 0);
        return intent.getComponent() == null ? "" : intent.getComponent().getPackageName();
    }

    private static String identity(JSONObject row) throws Exception {
        Intent intent = Intent.parseUri(row.optString("intent", ""), 0);
        return (intent.getComponent() == null ? "" : intent.getComponent().flattenToString())
                + ":" + row.optLong("profileId", -1) + ":" + row.optInt("cloneId", 0);
    }

    private static JSONArray readRows(SQLiteDatabase db) throws Exception {
        JSONArray result = new JSONArray();
        try (Cursor cursor = db.query("favorites", null, null, null, null, null, "_id ASC")) {
            while (cursor.moveToNext()) {
                JSONObject row = new JSONObject();
                for (int i = 0; i < cursor.getColumnCount(); i++) {
                    Object value = switch (cursor.getType(i)) {
                        case Cursor.FIELD_TYPE_NULL -> JSONObject.NULL;
                        case Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i);
                        case Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(i);
                        case Cursor.FIELD_TYPE_BLOB -> new JSONObject().put("blob", Base64.encodeToString(cursor.getBlob(i), Base64.NO_WRAP));
                        default -> cursor.getString(i);
                    };
                    row.put(cursor.getColumnName(i), value);
                }
                result.put(row);
            }
        }
        return result;
    }

    private static JSONObject jsonValues(ContentValues values) throws Exception {
        JSONObject row = new JSONObject();
        for (Map.Entry<String, Object> entry : values.valueSet()) {
            Object value = entry.getValue();
            row.put(entry.getKey(), value instanceof byte[] bytes
                    ? new JSONObject().put("blob", Base64.encodeToString(bytes, Base64.NO_WRAP))
                    : value == null ? JSONObject.NULL : value);
        }
        return row;
    }

    private static ContentValues contentValues(JSONObject row) throws Exception {
        ContentValues values = new ContentValues();
        Iterator<String> keys = row.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = row.get(key);
            if (value == JSONObject.NULL) values.putNull(key);
            else if (value instanceof JSONObject blob) values.put(key, Base64.decode(blob.getString("blob"), Base64.NO_WRAP));
            else if (value instanceof Float || value instanceof Double) values.put(key, ((Number) value).doubleValue());
            else if (value instanceof Number number) values.put(key, number.longValue());
            else values.put(key, value.toString());
        }
        return values;
    }

    private static String layoutHash(JSONObject state, JSONArray items) throws Exception {
        StringBuilder text = new StringBuilder(state.getString("database"))
                .append(':').append(state.getInt("columns")).append(':').append(state.getInt("rows"))
                .append(':').append(state.getJSONArray("screens"));
        for (int i = 0; i < items.length(); i++) {
            JSONObject row = items.getJSONObject(i);
            TreeSet<String> keys = new TreeSet<>();
            row.keys().forEachRemaining(keys::add);
            JSONArray fields = new JSONArray();
            for (String key : keys) {
                if (!key.equals("icon") && !key.equals("modified")) fields.put(new JSONArray().put(key).put(row.get(key)));
            }
            text.append('\n').append(fields);
        }
        return digest(text.toString());
    }

    private static String digest(String text) throws Exception {
        return Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
    }

    private static Object requiredField(Object object, String name) {
        Object value = ReflectUtils.getField(object, name);
        if (value == null) throw new IllegalStateException("当前桌面缺少字段：" + name);
        return value;
    }

    private static Object call(Object target, String name) throws Exception {
        return invoke(target, name, new Class<?>[0]);
    }

    private static Object call(Object target, String name, Class<?> type, Object arg) throws Exception {
        return invoke(target, name, new Class<?>[]{type}, arg);
    }

    private static Object invoke(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Class<?> type = target instanceof Class<?> clazz ? clazz : target.getClass();
        Method method = type.getMethod(name, types);
        return method.invoke(target instanceof Class<?> ? null : target, args);
    }
}
