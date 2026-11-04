package com.example.flymestatusbarsizer.feature.taskswitcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

import com.example.flymestatusbarsizer.config.SettingsStore;
import com.example.flymestatusbarsizer.feature.tasks.RecentTaskCard;

import java.util.HashSet;
import java.util.Set;

final class FlymeRecentLockState {
    static final String SETTING = "recent_lock_apps";

    private FlymeRecentLockState() { }

    static Set<String> read(Context context) {
        Set<String> result = new HashSet<>();
        String saved = Settings.System.getString(context.getContentResolver(), SETTING);
        if (saved != null) {
            for (String key : saved.split(";")) if (!key.isEmpty()) result.add(key);
        }
        return result;
    }

    static void write(Context context, Set<String> locked) {
        StringBuilder value = new StringBuilder();
        for (String key : locked) value.append(key).append(';');
        Settings.System.putString(context.getContentResolver(), SETTING, value.toString());
    }

    static String key(RecentTaskCard card) {
        return card.lockPackageName + ":" + card.userId;
    }

    static void migrateLegacy(Context context) {
        SharedPreferences preferences = SettingsStore.prefs(context);
        String legacy = SettingsStore.readString(preferences,
                SettingsStore.KEY_TASK_SWITCHER_LOCKED_APPS, "[]");
        if ("[]".equals(legacy)) return;
        Set<String> locked = read(context);
        try {
            org.json.JSONArray packages = new org.json.JSONArray(legacy);
            int userId = android.os.Process.myUid() / 100000;
            for (int i = 0; i < packages.length(); i++) {
                String packageName = packages.optString(i, "");
                if (!packageName.isEmpty()) locked.add(packageName + ":" + userId);
            }
            write(context, locked);
            preferences.edit().remove(SettingsStore.KEY_TASK_SWITCHER_LOCKED_APPS).apply();
        } catch (org.json.JSONException ignored) { }
    }
}
