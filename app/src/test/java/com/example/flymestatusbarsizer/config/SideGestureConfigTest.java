package com.example.flymestatusbarsizer.config;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import com.example.flymestatusbarsizer.MainActivity;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

import static com.example.flymestatusbarsizer.config.SettingsStore.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SideGestureConfigTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private BackupActivity activity;
    private SharedPreferences prefs;
    private File backup;

    @Before public void setUp() throws Exception {
        activity = Robolectric.buildActivity(BackupActivity.class).get();
        prefs = SettingsStore.prefs(activity);
        prefs.edit().clear().commit();
        Field field = MainActivity.class.getDeclaredField("prefs");
        field.setAccessible(true);
        field.set(activity, prefs);
        backup = folder.newFile("side-gesture.json");
    }

    @Test public void oldPreferencesRetainAllGestureAndBackgroundChoicesWithoutWrites() throws Exception {
        assertFalse(load(prefs).assistantGestureEnabled);
        writeLegacySettings();
        Map<String, ?> before = prefs.getAll();
        assertLegacyChoices(load(prefs));
        assertEquals(before, prefs.getAll());
        assertFalse(prefs.contains(KEY_SIDE_GESTURE_ACTION));
        prefs.edit().putBoolean(KEY_ASSISTANT_GESTURE_ENABLED, false).commit();
        assertFalse(load(prefs).assistantGestureEnabled);
    }

    @Test public void invalidActionFallsBackAndResetRestoresDisabledDefault() throws Exception {
        for (int value : new int[]{-1, 2, Integer.MAX_VALUE}) {
            prefs.edit().putInt(KEY_SIDE_GESTURE_ACTION, value).commit();
            assertEquals(SIDE_GESTURE_ACTION_GLOBAL_ASSISTANT, load(prefs).sideGestureAction);
        }
        prefs.edit().putString(KEY_SIDE_GESTURE_ACTION, "invalid").commit();
        assertEquals(SIDE_GESTURE_ACTION_GLOBAL_ASSISTANT, load(prefs).sideGestureAction);
        activity.resetAllSettings();
        assertFalse(load(prefs).assistantGestureEnabled);
        assertEquals(SIDE_GESTURE_ACTION_GLOBAL_ASSISTANT, load(prefs).sideGestureAction);
    }

    @Test public void actionAndLegacySettingsSurviveBackupAndOldBackupImport() throws Exception {
        writeLegacySettings();
        prefs.edit().putInt(KEY_SIDE_GESTURE_ACTION, SIDE_GESTURE_ACTION_GLOBAL_ASSISTANT).commit();
        backupAction("exportConfig");
        assertEquals("配置已导出", activity.message);
        JSONObject root = new JSONObject(new String(Files.readAllBytes(backup.toPath()), StandardCharsets.UTF_8));
        assertEquals(SIDE_GESTURE_ACTION_GLOBAL_ASSISTANT,
                root.getJSONObject("settings").getInt(KEY_SIDE_GESTURE_ACTION));
        prefs.edit().clear().commit();
        backupAction("importConfig");
        assertEquals("配置已导入", activity.message);
        assertLegacyChoices(load(prefs));

        root.getJSONObject("settings").remove(KEY_SIDE_GESTURE_ACTION);
        for (int version = 2; version <= 4; version++) {
            root.put("version", version);
            Files.write(backup.toPath(), root.toString().getBytes(StandardCharsets.UTF_8));
            backupAction("importConfig");
            assertEquals("配置已导入", activity.message);
            assertLegacyChoices(load(prefs));
        }
    }

    @Test public void selectedActionSyncsAndRemovingItRestoresRemoteDefault() throws Exception {
        writeLegacySettings();
        prefs.edit().putInt(KEY_SIDE_GESTURE_ACTION, SIDE_GESTURE_ACTION_GLOBAL_ASSISTANT).commit();
        SharedPreferences remote = activity.getSharedPreferences("side-gesture-remote", Context.MODE_PRIVATE);
        remote.edit().clear().commit();
        Field field = RemoteSettingsSync.class.getDeclaredField("remotePrefs");
        field.setAccessible(true);
        Object previous = field.get(null);
        try {
            field.set(null, remote);
            RemoteSettingsSync.syncFromLocal(activity);
            assertEquals(SIDE_GESTURE_ACTION_GLOBAL_ASSISTANT, remote.getInt(KEY_SIDE_GESTURE_ACTION, -1));
            assertLegacyChoices(load(remote));
            prefs.edit().remove(KEY_SIDE_GESTURE_ACTION).commit();
            RemoteSettingsSync.syncFromLocal(activity);
            assertFalse(remote.contains(KEY_SIDE_GESTURE_ACTION));
            assertEquals(SIDE_GESTURE_ACTION_GLOBAL_ASSISTANT, load(remote).sideGestureAction);
        } finally {
            field.set(null, previous);
        }
    }

    @Test public void taskScaleActionSurvivesBackupAndRemoteSync() throws Exception {
        prefs.edit().putBoolean(KEY_ASSISTANT_GESTURE_ENABLED, true)
                .putInt(KEY_SIDE_GESTURE_ACTION, SIDE_GESTURE_ACTION_TASK_SCALE).commit();
        assertEquals(SIDE_GESTURE_ACTION_TASK_SCALE, load(prefs).sideGestureAction);
        backupAction("exportConfig");
        prefs.edit().clear().commit();
        backupAction("importConfig");
        assertEquals(SIDE_GESTURE_ACTION_TASK_SCALE, load(prefs).sideGestureAction);
        SharedPreferences remote = activity.getSharedPreferences("task-scale-remote", Context.MODE_PRIVATE);
        remote.edit().clear().commit();
        Field field = RemoteSettingsSync.class.getDeclaredField("remotePrefs");
        field.setAccessible(true);
        Object previous = field.get(null);
        try {
            field.set(null, remote);
            RemoteSettingsSync.syncFromLocal(activity);
            assertEquals(SIDE_GESTURE_ACTION_TASK_SCALE, load(remote).sideGestureAction);
            assertTrue(load(remote).assistantGestureEnabled);
        } finally { field.set(null, previous); }
    }

    private void writeLegacySettings() {
        prefs.edit().putBoolean(KEY_ASSISTANT_GESTURE_ENABLED, true)
                .putInt(KEY_ASSISTANT_GESTURE_SIDE, ASSISTANT_GESTURE_SIDE_BOTH)
                .putInt(KEY_ASSISTANT_GESTURE_SCENES, ASSISTANT_GESTURE_SCENE_NOTIFICATION)
                .putInt(KEY_ASSISTANT_GESTURE_DISTANCE_DP, 180)
                .putInt(KEY_ASSISTANT_GESTURE_HOLD_MS, 900)
                .putBoolean(KEY_ASSISTANT_GESTURE_VERTICAL_LIMIT_ENABLED, true)
                .putInt(KEY_ASSISTANT_GESTURE_VERTICAL_LIMIT_DP, 64)
                .putBoolean(KEY_ASSISTANT_BACKGROUND_CUSTOM, true)
                .putBoolean(KEY_ASSISTANT_BACKGROUND_BLUR, false)
                .putInt(KEY_ASSISTANT_BACKGROUND_FOREGROUND_MODE, ASSISTANT_FOREGROUND_WHITE).commit();
    }

    private void assertLegacyChoices(ModuleConfig config) {
        assertEquals(SIDE_GESTURE_ACTION_GLOBAL_ASSISTANT, config.sideGestureAction);
        assertTrue(config.assistantGestureEnabled);
        assertEquals(ASSISTANT_GESTURE_SIDE_BOTH, config.assistantGestureSide);
        assertEquals(ASSISTANT_GESTURE_SCENE_NOTIFICATION, config.assistantGestureScenes);
        assertEquals(180, config.assistantGestureDistanceDp);
        assertEquals(900, config.assistantGestureHoldMs);
        assertTrue(config.assistantGestureVerticalLimitEnabled);
        assertEquals(64, config.assistantGestureVerticalLimitDp);
        assertTrue(config.assistantBackgroundCustom);
        assertFalse(config.assistantBackgroundBlur);
        assertEquals(ASSISTANT_FOREGROUND_WHITE, config.assistantBackgroundForegroundMode);
    }

    private ModuleConfig load(SharedPreferences source) throws Exception {
        Method method = ModuleConfig.class.getDeclaredMethod("fromSharedPreferences", SharedPreferences.class);
        method.setAccessible(true);
        return (ModuleConfig) method.invoke(null, source);
    }

    private void backupAction(String name) throws Exception {
        Method method = MainActivity.class.getDeclaredMethod(name, Uri.class);
        method.setAccessible(true);
        method.invoke(activity, Uri.fromFile(backup));
    }

    public static class BackupActivity extends MainActivity {
        String message;
        @Override public void showToast(String text) { message = text; }
        @Override public void recreate() { }
    }
}
