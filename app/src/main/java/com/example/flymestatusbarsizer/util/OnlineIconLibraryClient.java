package com.example.flymestatusbarsizer.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.flymestatusbarsizer.config.SettingsStore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class OnlineIconLibraryClient {
    public static final String TYPE_SILENT = "ringer_silent";
    public static final String TYPE_VIBRATE = "ringer_vibrate";
    public static final String TYPE_BATTERY = "battery";
    private static final String BASE_URL = "https://icon.biye.me";
    private static final int CONNECT_TIMEOUT_MS = 7000;
    private static final int READ_TIMEOUT_MS = 10000;
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;

    private OnlineIconLibraryClient() {
    }

    public record RemoteIcon(String id, String type, String name, String payload,
                             int likes, boolean liked, int downloadCount, boolean owned,
                             String createdAt) {
    }

    public static List<RemoteIcon> list(Context context, String type, String sort) throws IOException {
        String url = BASE_URL + "/v1/icons?type=" + encode(type) + "&sort=" + encode(sort)
                + "&limit=40";
        try {
            JSONObject root = new JSONObject(request(context, "GET", url, null));
            JSONArray items = root.optJSONArray("items");
            if (items == null) throw new IOException("在线库返回格式错误");
            ArrayList<RemoteIcon> result = new ArrayList<>(items.length());
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item != null) result.add(parseItem(item, type));
            }
            return result;
        } catch (JSONException invalid) {
            throw new IOException("在线库返回格式错误", invalid);
        }
    }

    public static RemoteIcon upload(Context context, String type, String name, String payload)
            throws IOException {
        try {
            JSONObject body = new JSONObject().put("type", type).put("name", name)
                    .put("payload", payload);
            JSONObject root = new JSONObject(request(context, "POST", BASE_URL + "/v1/icons",
                    body.toString()));
            return parseItem(root.optJSONObject("item"), type);
        } catch (JSONException invalid) {
            throw new IOException("在线库返回格式错误", invalid);
        }
    }

    public static RemoteIcon like(Context context, String id, boolean liked) throws IOException {
        try {
            String method = liked ? "PUT" : "DELETE";
            JSONObject root = new JSONObject(request(context, method,
                    BASE_URL + "/v1/icons/" + encode(id) + "/like", null));
            return parseItem(root.optJSONObject("item"), root.optString("type", ""));
        } catch (JSONException invalid) {
            throw new IOException("在线库返回格式错误", invalid);
        }
    }

    public static void delete(Context context, String id) throws IOException {
        request(context, "DELETE", BASE_URL + "/v1/icons/" + encode(id), null);
    }

    public static int recordDownload(Context context, String id) throws IOException {
        try {
            JSONObject response = new JSONObject(request(context, "POST",
                    BASE_URL + "/v1/icons/" + encode(id) + "/download", null));
            return Math.max(0, response.getInt("downloadCount"));
        } catch (JSONException invalid) {
            throw new IOException("在线库下载统计响应格式错误", invalid);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String request(Context context, String method, String url, String body)
            throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "FlymeStatusBarSizer");
            connection.setRequestProperty("X-Device-Id", deviceId(context));
            if (body != null) {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setFixedLengthStreamingMode(bytes.length);
                connection.getOutputStream().write(bytes);
            }
            int code = connection.getResponseCode();
            String response = readLimited(code >= 400 ? connection.getErrorStream()
                    : connection.getInputStream());
            if (code < 200 || code >= 300) {
                String detail = response == null || response.isBlank() ? "请求失败" : response;
                throw new IOException("在线库请求失败（" + code + "）：" + detail);
            }
            if (response == null) throw new IOException("在线库没有返回内容");
            return response;
        } catch (IllegalArgumentException invalid) {
            throw new IOException("在线库地址无效", invalid);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String deviceId(Context context) {
        SharedPreferences prefs = SettingsStore.prefs(context);
        String value = prefs.getString(SettingsStore.KEY_ICON_LIBRARY_DEVICE_ID, "");
        if (!value.isBlank()) return value;
        value = UUID.randomUUID().toString();
        prefs.edit().putString(SettingsStore.KEY_ICON_LIBRARY_DEVICE_ID, value).apply();
        return value;
    }

    private static String readLimited(InputStream source) throws IOException {
        if (source == null) return null;
        try (InputStream input = source; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_RESPONSE_BYTES) throw new IOException("在线库返回内容过大");
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8);
        }
    }

    private static RemoteIcon parseItem(JSONObject item, String expectedType) throws IOException {
        if (item == null) throw new IOException("在线库返回图标为空");
        String id = item.optString("id", "").trim();
        String type = item.optString("type", "").trim();
        String name = item.optString("name", "").trim();
        String payload = item.optString("payload", "");
        if (id.isEmpty() || name.isEmpty() || payload.isEmpty()
                || (!expectedType.isEmpty() && !expectedType.equals(type))) {
            throw new IOException("在线库图标数据无效");
        }
        return new RemoteIcon(id, type, name, payload, Math.max(0, item.optInt("likes", 0)),
                item.optBoolean("liked", false), Math.max(0, item.optInt("downloadCount", 0)),
                item.optBoolean("owned", false), item.optString("createdAt", ""));
    }
}
