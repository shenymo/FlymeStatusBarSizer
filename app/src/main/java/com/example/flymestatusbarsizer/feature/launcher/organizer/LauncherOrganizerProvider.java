package com.example.flymestatusbarsizer.feature.launcher.organizer;

import com.example.flymestatusbarsizer.BuildConfig;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Small, private-to-module/launcher mailbox. API credentials never enter this mailbox. */
public final class LauncherOrganizerProvider extends ContentProvider {
    static final Uri URI = Uri.parse("content://" + BuildConfig.APPLICATION_ID + ".organizer");
    static final String SIGNAL = "launcher_organizer_request";
    static final String LAUNCHER = "com.meizu.flyme.launcher";

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("launcher_organizer_mailbox", Context.MODE_PRIVATE);
    }

    static List<String> validateGroups(JSONArray apps, JSONArray groups) throws Exception {
        List<String> expected = new ArrayList<>();
        List<List<String>> members = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (int i = 0; i < apps.length(); i++) expected.add(apps.getJSONObject(i).getString("id"));
        for (int i = 0; i < groups.length(); i++) {
            JSONObject group = groups.getJSONObject(i);
            int type = group.optInt("folderType", -1);
            if (type != -1) LauncherOrganizerLayout.folderSpan(type);
            String name = group.getString("name").trim();
            if (name.isEmpty() || name.length() > 30 || !names.add(name)) throw new IllegalArgumentException("分类名称为空、过长或重复");
            group.put("name", name);
            JSONArray ids = group.getJSONArray("apps");
            List<String> list = new ArrayList<>();
            for (int j = 0; j < ids.length(); j++) list.add(ids.getString(j));
            members.add(list);
        }
        return LauncherOrganizerLayout.validateMembership(expected, members);
    }

    @Override public boolean onCreate() { return true; }

    @Override public synchronized Bundle call(String method, String arg, Bundle extras) {
        boolean owner = Binder.getCallingUid() == Process.myUid();
        if (!owner && !LAUNCHER.equals(getCallingPackage())) {
            throw new SecurityException("Only the module and Flyme Launcher may access this provider");
        }
        SharedPreferences prefs = prefs(getContext());
        Bundle result = new Bundle();
        try {
            if ("submit".equals(method)) {
                if (!owner) throw new SecurityException("Only the module may submit commands");
                long age = System.currentTimeMillis() - prefs.getLong("time", 0);
                String state = prefs.getString("state", "");
                if (("pending".equals(state) || "running".equals(state)) && age < 120_000) {
                    throw new IllegalStateException("上一项桌面操作尚未完成");
                }
                if (!"read".equals(arg) && !"apply".equals(arg) && !"sort_usage".equals(arg)) {
                    throw new IllegalArgumentException("未知操作");
                }
                String id = UUID.randomUUID().toString();
                JSONObject request = new JSONObject().put("id", id).put("action", arg)
                        .put("data", extras == null ? "" : extras.getString("data", ""));
                if (!prefs.edit().clear().putString("id", id).putString("state", "pending")
                        .putLong("time", System.currentTimeMillis())
                        .putString("request", request.toString()).commit()) {
                    throw new IllegalStateException("无法保存桌面操作");
                }
                result.putString("id", id);
            } else if ("take".equals(method)) {
                if ("pending".equals(prefs.getString("state", ""))) {
                    if (System.currentTimeMillis() - prefs.getLong("time", 0) > 45_000) {
                        prefs.edit().putString("state", "done").putString("error", "桌面连接超时，请重新操作").commit();
                    } else {
                        result.putString("request", prefs.getString("request", ""));
                        if (!prefs.edit().putString("state", "running").commit()) {
                            throw new IllegalStateException("无法领取桌面操作");
                        }
                    }
                }
            } else if ("finish".equals(method)) {
                if (arg != null && arg.equals(prefs.getString("id", ""))) {
                    prefs.edit().putString("state", "done")
                            .putString("data", extras.getString("data", ""))
                            .putString("error", extras.getString("error", "")).commit();
                }
            } else if ("status".equals(method)) {
                if (!owner) throw new SecurityException("Only the module may query command status");
                if (arg != null && arg.equals(prefs.getString("id", ""))) {
                    result.putString("id", arg);
                    result.putString("state", prefs.getString("state", ""));
                    result.putString("data", prefs.getString("data", ""));
                    result.putString("error", prefs.getString("error", ""));
                }
            } else if ("cancel".equals(method)) {
                if (!owner) throw new SecurityException("Only the module may cancel commands");
                if (arg != null && arg.equals(prefs.getString("id", ""))
                        && "pending".equals(prefs.getString("state", ""))) {
                    prefs.edit().putString("state", "done").putString("error", "桌面未响应").commit();
                }
            } else {
                throw new IllegalArgumentException("Unknown method");
            }
        } catch (org.json.JSONException e) {
            throw new IllegalArgumentException(e);
        }
        return result;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) { throw new UnsupportedOperationException(); }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
