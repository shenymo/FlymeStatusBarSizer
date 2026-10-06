package com.example.flymestatusbarsizer.feature.assistant;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AssistantTitleColorTest {
    @Test public void globalTintFollowsTransitionsAndRestoresStatefulDesktopColors() {
        TextView title = new TextView(RuntimeEnvironment.getApplication());
        ColorStateList nativeColors = new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_pressed}, new int[]{}},
                new int[]{Color.RED, Color.BLACK});
        title.setTextColor(nativeColors);
        AssistantTitleColor color = new AssistantTitleColor(title);
        color.setTint(Color.WHITE);
        assertSame(nativeColors, title.getTextColors());
        color.apply();
        assertEquals(Color.WHITE, title.getCurrentTextColor());
        color.setTint(0xff777777);
        assertEquals(0xff777777, title.getCurrentTextColor());
        color.setTint(Color.BLACK);
        assertEquals(Color.BLACK, title.getCurrentTextColor());
        color.restore();
        assertSame(nativeColors, title.getTextColors());
        color.setTint(Color.WHITE);
        title.getViewTreeObserver().dispatchOnPreDraw();
        assertSame(nativeColors, title.getTextColors());
    }

    @Test public void wallpaperChangesAreSuppressedGloballyButPreservedForDesktop() {
        TextView title = new TextView(RuntimeEnvironment.getApplication());
        title.setTextColor(Color.WHITE);
        AssistantTitleColor color = new AssistantTitleColor(title);
        color.setTint(Color.BLACK);
        color.apply();
        ColorStateList newWallpaperColors = ColorStateList.valueOf(0xffeeeeee);
        title.setTextColor(newWallpaperColors);
        title.getViewTreeObserver().dispatchOnPreDraw();
        assertEquals(Color.BLACK, title.getCurrentTextColor());
        color.restore();
        assertSame(newWallpaperColors, title.getTextColors());
        title.setTextColor(Color.WHITE);
        title.getViewTreeObserver().dispatchOnPreDraw();
        assertEquals(Color.WHITE, title.getCurrentTextColor());
        color.apply();
        assertEquals(Color.BLACK, title.getCurrentTextColor());
        color.restore();
        assertEquals(Color.WHITE, title.getCurrentTextColor());
    }

    @Test public void missingTintOrCancelledAttachmentDoesNotChangeNativeTitle() {
        TextView title = new TextView(RuntimeEnvironment.getApplication());
        title.setTextColor(Color.RED);
        AssistantTitleColor color = new AssistantTitleColor(title);
        color.setTint(Color.BLACK);
        color.restore();
        assertEquals(Color.RED, title.getCurrentTextColor());
        color.setTint(null);
        color.apply();
        color.onPreDraw();
        assertEquals(Color.RED, title.getCurrentTextColor());
        color.restore();
        new AssistantTitleColor((TextView) null).apply();
    }
}
