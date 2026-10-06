package com.example.flymestatusbarsizer.feature.notification.anip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.flymestatusbarsizer.config.ModuleConfig;
import com.example.flymestatusbarsizer.config.SettingsStore;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.LinkedHashMap;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public final class AnipIconModeTest {
    private SharedPreferences prefs;

    @Before public void setUp() {
        prefs = SettingsStore.prefs(RuntimeEnvironment.getApplication());
        prefs.edit().clear().commit();
    }

    @Test public void disabledByDefaultSoExistingBehaviourIsUnchanged() {
        assertFalse(SettingsStore.DEFAULT_ANIP_ICON_ENABLED);
        assertEquals("{}", SettingsStore.DEFAULT_ANIP_ICON_MODE);
        // With the switch off and no overrides, every application must resolve to the desktop icon.
        assertEquals(AnipIconMode.APPLICATION,
                AnipIconMode.get(prefs, false, "com.tencent.mm"));
        assertFalse(AnipIconMode.mayUseAnip(prefs, false, "com.tencent.mm"));
    }

    @Test public void enablingTheSwitchMakesApplicationsFollowIt() {
        assertEquals(AnipIconMode.FOLLOW, AnipIconMode.get(prefs, true, "com.tencent.mm"));
        assertTrue(AnipIconMode.mayUseAnip(prefs, true, "com.tencent.mm"));
    }

    @Test public void perApplicationOverrideWinsOverTheGlobalSwitch() {
        Map<String, Integer> overrides = new LinkedHashMap<>();
        overrides.put("com.tencent.mm", AnipIconMode.APPLICATION);
        overrides.put("com.taobao.taobao", AnipIconMode.ANIP);
        prefs.edit().putString(SettingsStore.KEY_ANIP_ICON_MODE,
                AnipIconMode.encodeOverrides(overrides)).commit();

        // Switch on: WeChat is pinned back to the desktop icon, Taobao stays on ANIP.
        assertEquals(AnipIconMode.APPLICATION, AnipIconMode.get(prefs, true, "com.tencent.mm"));
        assertEquals(AnipIconMode.ANIP, AnipIconMode.get(prefs, true, "com.taobao.taobao"));
        // Switch off: Taobao is still explicitly pinned to ANIP.
        assertEquals(AnipIconMode.ANIP, AnipIconMode.get(prefs, false, "com.taobao.taobao"));
        assertEquals(AnipIconMode.APPLICATION, AnipIconMode.get(prefs, false, "com.qq.music"));
    }

    @Test public void overridesRoundTripThroughTheCodec() {
        Map<String, Integer> overrides = new LinkedHashMap<>();
        overrides.put("a.b.c", AnipIconMode.ANIP);
        overrides.put("d.e.f", AnipIconMode.APPLICATION);
        String encoded = AnipIconMode.encodeOverrides(overrides);
        assertEquals(overrides, AnipIconMode.decodeOverrides(encoded));
    }

    @Test public void followRemovesTheOverrideInsteadOfPinningCurrentBehaviour() {
        Map<String, Integer> overrides = new LinkedHashMap<>();
        overrides.put("a.b.c", AnipIconMode.ANIP);
        Map<String, Integer> cleared = AnipIconMode.withOverride(overrides, "a.b.c", AnipIconMode.FOLLOW);
        assertTrue(cleared.isEmpty());
        assertEquals(AnipIconMode.FOLLOW, AnipIconMode.get(prefs, true, "a.b.c"));
    }

    @Test public void unknownAndMalformedValuesAreIgnored() {
        // Encode refuses nothing it does not understand, and decoding drops unknown modes.
        Map<String, Integer> dirty = new LinkedHashMap<>();
        dirty.put("good.app", AnipIconMode.ANIP);
        dirty.put("bad.mode", 99);
        dirty.put("follow.app", AnipIconMode.FOLLOW);
        Map<String, Integer> decoded = AnipIconMode.decodeOverrides(AnipIconMode.encodeOverrides(dirty));
        assertEquals(1, decoded.size());
        assertEquals(Integer.valueOf(AnipIconMode.ANIP), decoded.get("good.app"));

        assertTrue(AnipIconMode.decodeOverrides("not json").isEmpty());
        assertTrue(AnipIconMode.decodeOverrides("").isEmpty());
        assertTrue(AnipIconMode.decodeOverrides(null).isEmpty());
        assertTrue(AnipIconMode.decodeOverrides("{}").isEmpty());
        assertEquals("{}", AnipIconMode.encodeOverrides(null));
        assertEquals("{}", AnipIconMode.encodeOverrides(new LinkedHashMap<>()));
    }

    @Test public void moduleConfigDefaultsKeepAnipOffAndUnconfigured() {
        ModuleConfig defaults = new ModuleConfig();
        assertFalse(defaults.anipIconEnabled);
        assertEquals(SettingsStore.DEFAULT_ANIP_ICON_MODE, defaults.anipIconMode);
    }

    @Test public void anipRequiresTheDesktopIconFeatureToBeOn() {
        // ANIP only replaces the desktop-icon fallback, so it must never activate on its own.
        ModuleConfig anipOnly = new ModuleConfig();
        anipOnly.enabled = true;
        anipOnly.anipIconEnabled = true;
        anipOnly.notificationAppIconEnabled = false;
        assertFalse(anipActive(anipOnly));

        ModuleConfig both = new ModuleConfig();
        both.enabled = true;
        both.anipIconEnabled = true;
        both.notificationAppIconEnabled = true;
        assertTrue(anipActive(both));
    }

    @Test public void anipStaysOffWhenTheModuleItselfIsDisabled() {
        ModuleConfig disabled = new ModuleConfig();
        disabled.enabled = false;
        disabled.notificationAppIconEnabled = true;
        disabled.anipIconEnabled = true;
        assertFalse(anipActive(disabled));
        assertFalse(anipActive(null));
    }

    @Test public void anipStaysOffWhenTheUserDidNotOptIn() {
        ModuleConfig notOptedIn = new ModuleConfig();
        notOptedIn.enabled = true;
        notOptedIn.notificationAppIconEnabled = true;
        notOptedIn.anipIconEnabled = false;
        assertFalse(anipActive(notOptedIn));
    }

    @Test public void overridesFallBackToTheEmptyDefault() {
        assertEquals("{}", new ModuleConfig().anipOverrides());
        ModuleConfig config = new ModuleConfig();
        config.anipIconMode = "{\"a.b.c\":1}";
        assertEquals("{\"a.b.c\":1}", config.anipOverrides());
        config.anipIconMode = null;
        assertEquals("{}", config.anipOverrides());
    }

    /**
     * The module-level ANIP precondition, mirroring what the notification flow applies before it
     * consults the library.
     */
    private static boolean anipActive(ModuleConfig config) {
        return config != null
                && config.enabled
                && config.notificationAppIconEnabled
                && config.anipIconEnabled;
    }
}
