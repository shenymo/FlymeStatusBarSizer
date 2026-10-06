package com.example.flymestatusbarsizer.config;

import static org.junit.Assert.assertEquals;

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

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public final class AnipConfigBackupTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private BackupActivity activity;
    private SharedPreferences prefs;
    private File backup;

    public static class BackupActivity extends MainActivity {
        String message;

        @Override public void showToast(String text) { message = text; }
        @Override public void recreate() { }
    }

    @Before public void setUp() throws Exception {
        // Attach a real ContentResolver without starting the settings UI or background services.
        activity = Robolectric.buildActivity(BackupActivity.class).get();
        prefs = SettingsStore.prefs(activity);
        prefs.edit().clear().commit();
        Field field = MainActivity.class.getDeclaredField("prefs");
        field.setAccessible(true);
        field.set(activity, prefs);
        backup = temporaryFolder.newFile("config.json");
    }

    @Test public void allSourceAndColorChoicesSurviveJsonRoundTrip() throws Exception {
        for (int source = 0; source <= 2; source++) {
            for (int color = 0; color <= 2; color++) {
                prefs.edit().clear()
                        .putInt(SettingsStore.KEY_ANIP_SOURCE_TYPE, source)
                        .putInt(SettingsStore.KEY_ANIP_ICON_COLOR_MODE, color).commit();
                invokeBackupAction("exportConfig");
                assertEquals("配置已导出", activity.message);
                JSONObject root = new JSONObject(new String(
                        Files.readAllBytes(backup.toPath()), StandardCharsets.UTF_8));
                assertEquals(4, root.getInt("version"));
                JSONObject settings = root.getJSONObject("settings");
                assertEquals(Integer.valueOf(source), settings.get(SettingsStore.KEY_ANIP_SOURCE_TYPE));
                assertEquals(Integer.valueOf(color), settings.get(SettingsStore.KEY_ANIP_ICON_COLOR_MODE));

                prefs.edit().clear().commit();
                invokeBackupAction("importConfig");
                assertEquals("配置已导入", activity.message);
                assertIntegerChoices(source, color);
            }
        }
    }

    @Test public void numericChoicesImportFromEverySupportedVersion() throws Exception {
        for (int version = 2; version <= 4; version++) {
            importSettings(version, new JSONObject()
                    .put(SettingsStore.KEY_ANIP_SOURCE_TYPE, 2)
                    .put(SettingsStore.KEY_ANIP_ICON_COLOR_MODE, 1));
            assertIntegerChoices(2, 1);
        }
    }

    @Test public void legacyBooleanOrMissingChoicesFallBackToIntegerDefaults() throws Exception {
        for (int version = 2; version <= 4; version++) {
            // Old boolean exports lost the original enum value; do not guess a selection.
            for (Object value : new Object[]{false, true, null}) {
                importSettings(version, new JSONObject()
                        .put(SettingsStore.KEY_ANIP_SOURCE_TYPE, value)
                        .put(SettingsStore.KEY_ANIP_ICON_COLOR_MODE, value)
                        .put(SettingsStore.KEY_ANIP_ICON_ENABLED, true));
                assertIntegerChoices(SettingsStore.DEFAULT_ANIP_SOURCE_TYPE,
                        SettingsStore.DEFAULT_ANIP_ICON_COLOR_MODE);
                assertEquals(Boolean.TRUE, prefs.getAll().get(SettingsStore.KEY_ANIP_ICON_ENABLED));
            }
        }
    }

    private void importSettings(int version, JSONObject settings) throws Exception {
        JSONObject root = new JSONObject().put("schema", "flyme_status_bar_sizer")
                .put("version", version).put("settings", settings);
        Files.write(backup.toPath(), root.toString().getBytes(StandardCharsets.UTF_8));
        invokeBackupAction("importConfig");
        assertEquals("配置已导入", activity.message);
    }

    private void assertIntegerChoices(int source, int color) {
        assertEquals(Integer.valueOf(source), prefs.getAll().get(SettingsStore.KEY_ANIP_SOURCE_TYPE));
        assertEquals(Integer.valueOf(color), prefs.getAll().get(SettingsStore.KEY_ANIP_ICON_COLOR_MODE));
        assertEquals(source, SettingsStore.readInt(prefs, SettingsStore.KEY_ANIP_SOURCE_TYPE, -1));
        assertEquals(color, SettingsStore.readInt(prefs, SettingsStore.KEY_ANIP_ICON_COLOR_MODE, -1));
    }

    private void invokeBackupAction(String name) throws Exception {
        Method method = MainActivity.class.getDeclaredMethod(name, Uri.class);
        method.setAccessible(true);
        activity.message = null;
        method.invoke(activity, Uri.fromFile(backup));
    }
}
