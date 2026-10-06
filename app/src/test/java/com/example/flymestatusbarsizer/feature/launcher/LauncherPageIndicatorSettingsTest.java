package com.example.flymestatusbarsizer.feature.launcher;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;
import com.example.flymestatusbarsizer.config.ModuleConfig;
import com.example.flymestatusbarsizer.config.SettingsStore;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class LauncherPageIndicatorSettingsTest {
    @Test public void optInSyncBackupAndMasterSwitchUseTheSameConfiguration() throws Exception {
        String key = SettingsStore.KEY_LAUNCHER_PAGE_INDICATOR_SWIPE_ENABLED;
        assertEquals(1, Arrays.stream(SettingsStore.BOOLEAN_KEYS).filter(key::equals).count());
        assertFalse(SettingsStore.defaultBoolean(key));
        SharedPreferences prefs = RuntimeEnvironment.getApplication()
                .getSharedPreferences("indicator_swipe", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        assertFalse(snapshot(prefs).launcherPageIndicatorSwipeEnabled);
        prefs.edit().putBoolean(key, true).putBoolean("enabled", true).commit();
        assertTrue(snapshot(prefs).launcherPageIndicatorSwipeEnabled);
        prefs.edit().putBoolean("enabled", false).commit();
        assertFalse(snapshot(prefs).launcherPageIndicatorSwipeEnabled);
        prefs.edit().putBoolean("enabled", true).commit();
        assertTrue(snapshot(prefs).launcherPageIndicatorSwipeEnabled);
        prefs.edit().putBoolean(key, false).commit();
        assertFalse(snapshot(prefs).launcherPageIndicatorSwipeEnabled);
        prefs.edit().remove(key).commit();
        assertFalse(snapshot(prefs).launcherPageIndicatorSwipeEnabled);
    }

    private FlymeStatusBarSizer.LauncherAppearanceConfigSnapshot snapshot(SharedPreferences prefs)
            throws Exception {
        Method load = ModuleConfig.class.getDeclaredMethod("fromSharedPreferences", SharedPreferences.class);
        load.setAccessible(true);
        Constructor<FlymeStatusBarSizer.LauncherAppearanceConfigSnapshot> constructor =
                FlymeStatusBarSizer.LauncherAppearanceConfigSnapshot.class.getDeclaredConstructor(ModuleConfig.class);
        constructor.setAccessible(true);
        return constructor.newInstance(load.invoke(null, prefs));
    }
}
