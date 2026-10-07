package com.example.flymestatusbarsizer.feature.assistant;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.ParcelFileDescriptor;
import android.net.Uri;

import com.example.flymestatusbarsizer.config.ModuleConfig;
import com.example.flymestatusbarsizer.config.SettingsStore;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AssistantBackgroundImagesTest {
    @Test public void blurSoftensAnEdgeWithoutChangingTheSourceOrUniformRegions() {
        Bitmap source = Bitmap.createBitmap(100, 60, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < 60; y++) for (int x = 0; x < 100; x++)
            source.setPixel(x, y, x < 50 ? Color.BLACK : Color.WHITE);
        Bitmap blurred = AssistantBackgroundImages.blur(source);
        assertEquals(Color.BLACK, source.getPixel(49, 30));
        assertEquals(Color.WHITE, source.getPixel(50, 30));
        assertTrue(Color.red(blurred.getPixel(49, 30)) > 0);
        assertTrue(Color.red(blurred.getPixel(50, 30)) < 255);
        assertEquals(Color.BLACK, blurred.getPixel(0, 30));
        assertEquals(Color.WHITE, blurred.getPixel(99, 30));
        source.recycle(); blurred.recycle();
    }

    @Test public void blurHandlesSinglePixelAndLimitsWorkingResolution() {
        Bitmap source = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        source.eraseColor(Color.RED);
        Bitmap blurred = AssistantBackgroundImages.blur(source);
        assertEquals(Color.RED, blurred.getPixel(0, 0));
        assertFalse(source.isRecycled());
        source.recycle(); blurred.recycle();
        source = Bitmap.createBitmap(2048, 1024, Bitmap.Config.ARGB_8888);
        blurred = AssistantBackgroundImages.blur(source);
        assertEquals(512, blurred.getWidth());
        assertEquals(256, blurred.getHeight());
        source.recycle(); blurred.recycle();
    }

    @Test public void providerOnlyOpensKnownVariantsReadOnlyAndCleanupKeepsTheActivePair() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        String id = "0123456789abcdef0123456789abcdef";
        String old = "fedcba9876543210fedcba9876543210";
        AssistantBackgroundImages.directory(context).mkdirs();
        for (String key : new String[]{id, old}) for (boolean blur : new boolean[]{true, false})
            try (FileOutputStream out = new FileOutputStream(AssistantBackgroundImages.file(context, key, blur))) {
                out.write(new byte[]{1, 2, 3});
            }
        AssistantBackgroundProvider provider = Robolectric.buildContentProvider(AssistantBackgroundProvider.class).create().get();
        try (ParcelFileDescriptor fd = provider.openFile(AssistantBackgroundProvider.uri(id, false), "r")) {
            assertEquals(3, fd.getStatSize());
        }
        assertThrows(java.io.FileNotFoundException.class,
                () -> provider.openFile(AssistantBackgroundProvider.uri(id, false), "rw"));
        Uri traversal = AssistantBackgroundProvider.uri(id, false).buildUpon().path("/../clear").build();
        assertThrows(java.io.FileNotFoundException.class, () -> provider.openFile(traversal, "r"));
        assertThrows(IllegalArgumentException.class, () -> AssistantBackgroundImages.file(context, "../secret", false));
        AssistantBackgroundImages.deleteUnused(context, id);
        assertTrue(AssistantBackgroundImages.file(context, id, false).exists());
        assertTrue(AssistantBackgroundImages.file(context, id, true).exists());
        assertFalse(AssistantBackgroundImages.file(context, old, false).exists());
        assertFalse(AssistantBackgroundImages.file(context, old, true).exists());
        AssistantBackgroundImages.deleteUnused(context, "");
        assertFalse(AssistantBackgroundImages.file(context, id, false).exists());
    }

    @Test public void configDefaultsAndCustomChoiceAreIncludedInSyncAndBackup() throws Exception {
        android.content.SharedPreferences prefs = RuntimeEnvironment.getApplication()
                .getSharedPreferences("background-config", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        java.lang.reflect.Method load = ModuleConfig.class.getDeclaredMethod("fromSharedPreferences", android.content.SharedPreferences.class);
        load.setAccessible(true);
        ModuleConfig defaults = (ModuleConfig) load.invoke(null, prefs);
        assertEquals(SettingsStore.ASSISTANT_FOREGROUND_FOLLOW_STATUS_BAR, defaults.assistantBackgroundForegroundMode);
        assertTrue(defaults.assistantBackgroundBlur);
        assertFalse(defaults.assistantBackgroundCustom);
        assertEquals("", defaults.assistantBackgroundImage);
        prefs.edit().putInt(SettingsStore.KEY_ASSISTANT_BACKGROUND_FOREGROUND_MODE, SettingsStore.ASSISTANT_FOREGROUND_WHITE)
                .putBoolean(SettingsStore.KEY_ASSISTANT_BACKGROUND_BLUR, false)
                .putBoolean(SettingsStore.KEY_ASSISTANT_BACKGROUND_CUSTOM, true)
                .putString(SettingsStore.KEY_ASSISTANT_BACKGROUND_IMAGE, "0123456789abcdef0123456789abcdef").commit();
        ModuleConfig custom = (ModuleConfig) load.invoke(null, prefs);
        assertEquals(SettingsStore.ASSISTANT_FOREGROUND_WHITE, custom.assistantBackgroundForegroundMode);
        assertFalse(custom.assistantBackgroundBlur);
        assertTrue(custom.assistantBackgroundCustom);
        assertEquals("0123456789abcdef0123456789abcdef", custom.assistantBackgroundImage);
        assertTrue(Arrays.asList(SettingsStore.BOOLEAN_KEYS).contains(SettingsStore.KEY_ASSISTANT_BACKGROUND_BLUR));
        assertTrue(Arrays.asList(SettingsStore.BOOLEAN_KEYS).contains(SettingsStore.KEY_ASSISTANT_BACKGROUND_CUSTOM));
        assertTrue(Arrays.asList(SettingsStore.STRING_KEYS).contains(SettingsStore.KEY_ASSISTANT_BACKGROUND_IMAGE));
        assertTrue(Arrays.asList(SettingsStore.INT_KEYS).contains(SettingsStore.KEY_ASSISTANT_BACKGROUND_FOREGROUND_MODE));
        assertEquals(SettingsStore.ASSISTANT_FOREGROUND_FOLLOW_STATUS_BAR,
                SettingsStore.defaultInt(SettingsStore.KEY_ASSISTANT_BACKGROUND_FOREGROUND_MODE));
        prefs.edit().putInt(SettingsStore.KEY_ASSISTANT_BACKGROUND_FOREGROUND_MODE, -1).commit();
        assertEquals(SettingsStore.ASSISTANT_FOREGROUND_FOLLOW_STATUS_BAR,
                ((ModuleConfig) load.invoke(null, prefs)).assistantBackgroundForegroundMode);
        assertTrue(SettingsStore.defaultBoolean(SettingsStore.KEY_ASSISTANT_BACKGROUND_BLUR));
        assertFalse(SettingsStore.defaultBoolean(SettingsStore.KEY_ASSISTANT_BACKGROUND_CUSTOM));
    }
}
