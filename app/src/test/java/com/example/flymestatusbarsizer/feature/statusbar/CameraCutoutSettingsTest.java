package com.example.flymestatusbarsizer.feature.statusbar;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.flymestatusbarsizer.config.ModuleConfig;
import com.example.flymestatusbarsizer.config.SettingsStore;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.lang.reflect.Method;
import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class CameraCutoutSettingsTest {
    @Test public void optInSurvivesLoadingAndParticipatesInSyncBackupAndReset() throws Exception {
        String key = SettingsStore.KEY_HIDE_IDLE_CAMERA_CUTOUT;
        assertEquals(1, Arrays.stream(SettingsStore.BOOLEAN_KEYS).filter(key::equals).count());
        assertFalse(SettingsStore.defaultBoolean(key));
        SharedPreferences prefs = RuntimeEnvironment.getApplication()
                .getSharedPreferences("camera_cutout", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        assertFalse(load(prefs).hideIdleCameraCutout);
        prefs.edit().putBoolean(key, true).commit();
        assertTrue(load(prefs).hideIdleCameraCutout);
        prefs.edit().putBoolean("enabled", false).commit();
        assertFalse(load(prefs).enabled);
        assertTrue(load(prefs).hideIdleCameraCutout);
        prefs.edit().putBoolean(key, false).commit();
        assertFalse(load(prefs).hideIdleCameraCutout);
        prefs.edit().remove(key).commit();
        assertFalse(load(prefs).hideIdleCameraCutout);
    }

    private static ModuleConfig load(SharedPreferences prefs) throws Exception {
        Method method = ModuleConfig.class.getDeclaredMethod("fromSharedPreferences", SharedPreferences.class);
        method.setAccessible(true);
        return (ModuleConfig) method.invoke(null, prefs);
    }
}
